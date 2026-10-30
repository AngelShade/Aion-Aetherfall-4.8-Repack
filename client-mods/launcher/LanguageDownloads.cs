using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Threading;
using System.Web.Script.Serialization;
using AetherfallUpdate;

namespace Aetherfall {
 public sealed class LanguagePackFile { public string path,sha256;public long bytes; }
 public sealed class LanguageDownload { public string id,code,kind,version,file,url,sha256;public long bytes;public List<LanguagePackFile> files=new List<LanguagePackFile>(); }
 public sealed class LanguageDownloadCatalog { public int schema=1;public List<LanguageDownload> packs=new List<LanguageDownload>();public List<LanguageDownload> previousVersions=new List<LanguageDownload>(); }
 public static class LanguageDownloads {
  static readonly JavaScriptSerializer Json=new JavaScriptSerializer { MaxJsonLength=16*1024*1024 };
  static string At(string root,string relative){return Path.Combine(root,relative.Replace('/',Path.DirectorySeparatorChar));}
  static string Hash(string path){return UpdateTransfer.Hash(path,"",0,0,0,0,"");}
  static void Validate(LanguageDownload pack) {
   if(pack==null||!System.Text.RegularExpressions.Regex.IsMatch(pack.code??"",@"^(kor|jpn|deu|fra|esn|rus|chs)$")||
      (pack.kind!="text"&&pack.kind!="voice")||pack.id!=pack.code+"-"+pack.kind||pack.bytes<=0||pack.bytes>=2L*1024*1024*1024||
      !System.Text.RegularExpressions.Regex.IsMatch(pack.sha256??"",@"^[a-f0-9]{64}$")||!System.Text.RegularExpressions.Regex.IsMatch(pack.version??"",@"^\d+\.\d+\.\d+$")||
      String.IsNullOrEmpty(pack.file)||Path.GetFileName(pack.file)!=pack.file||pack.files==null||pack.files.Count==0||pack.files.Count>128||pack.files.Sum(x=>x.bytes)>8L*1024*1024*1024)
    throw new InvalidDataException("Invalid language pack catalog entry.");
   string prefix="Mods/Languages/Packs/"+pack.code+"/";
   var paths=new HashSet<string>(StringComparer.OrdinalIgnoreCase);
   foreach(var file in pack.files) {
    if(file==null||String.IsNullOrEmpty(file.path)||!file.path.StartsWith(prefix,StringComparison.Ordinal)||file.path.Contains('\\')||file.path.Contains(':')||file.path.Split('/').Any(x=>x=="."||x==".."||x.Length==0)||
       !paths.Add(file.path)||file.bytes<=0||!System.Text.RegularExpressions.Regex.IsMatch(file.sha256??"",@"^[a-f0-9]{64}$"))throw new InvalidDataException("Invalid language pack file.");
    string part=file.path.Substring(prefix.Length);
    if(pack.kind=="text"?part!="data/data.pak":!part.StartsWith("sounds/",StringComparison.Ordinal)||!part.EndsWith(".pak",StringComparison.OrdinalIgnoreCase))throw new InvalidDataException("Unexpected language component payload.");
   }
  }
  static LanguageDownloadCatalog ReadCatalog(string root) {
   string path=At(root,"Mods/Languages/catalog.json");if(!File.Exists(path))return new LanguageDownloadCatalog();
   var catalog=Json.Deserialize<LanguageDownloadCatalog>(File.ReadAllText(path));if(catalog==null||catalog.schema!=1||catalog.packs==null||catalog.previousVersions==null)throw new InvalidDataException("Unsupported language catalog.");
   var ids=new HashSet<string>();var versions=new HashSet<string>();
   foreach(var pack in catalog.packs){Validate(pack);if(!ids.Add(pack.id))throw new InvalidDataException("Duplicate language pack.");}
   foreach(var pack in catalog.packs.Concat(catalog.previousVersions)){Validate(pack);if(!versions.Add(pack.id+"/"+pack.version))throw new InvalidDataException("Duplicate language pack version.");}
   return catalog;
  }
  public static List<LanguageDownload> Catalog(string root) { return ReadCatalog(root).packs; }
  static LanguageDownload KnownPack(string root,string id,string version) {
   var catalog=ReadCatalog(root);var pack=catalog.packs.Concat(catalog.previousVersions).FirstOrDefault(x=>x.id==id&&x.version==version);
   if(pack==null)throw new InvalidDataException("Update the launcher catalog before installing this language pack.");return pack;
  }
  static void Closed() {
   foreach(string name in new[]{"aion","aion.bin"})foreach(Process process in Process.GetProcessesByName(name))using(process)if(!process.HasExited)throw new InvalidOperationException("Close Aion normally before installing a language pack. Your verified download is retained.");
  }
  static void NoLinks(string root,string relative) {
   string path=root;foreach(string part in relative.Split('/')){path=Path.Combine(path,part);if((File.Exists(path)||Directory.Exists(path))&&(File.GetAttributes(path)&FileAttributes.ReparsePoint)!=0)throw new IOException("Language paths must not be links.");}
  }
  public static string Fetch(string root,LanguageDownload pack,CancellationToken cancel) {
   Validate(pack);pack=KnownPack(root,pack.id,pack.version);string folder=At(root,"Mods/Languages/Cache");NoLinks(root,"Mods/Languages/Cache");Directory.CreateDirectory(folder);
   string path=Path.Combine(folder,pack.sha256+".zip");UpdateTransfer.DownloadResumable(pack.url,path,pack.bytes,pack.sha256,cancel);return path;
  }
  public static LanguageDownload Identify(string root,string archive) {
   using(var zip=ZipFile.OpenRead(archive)) {
    var entry=zip.GetEntry("language-pack.json");if(entry==null||entry.Length>1024*1024)throw new InvalidDataException("This is not an Aetherfall language pack.");
    LanguageDownload header;using(var reader=new StreamReader(entry.Open()))header=Json.Deserialize<LanguageDownload>(reader.ReadToEnd());
    if(header==null)throw new InvalidDataException("Invalid language pack header.");return KnownPack(root,header.id,header.version);
   }
  }
  public static void Install(string root,LanguageDownload pack,string archive) {
   Validate(pack);pack=KnownPack(root,pack.id,pack.version);Closed();root=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
   if(new FileInfo(archive).Length!=pack.bytes||!String.Equals(UpdateTransfer.Hash(archive,"verifying",0,pack.bytes,1,1,pack.file),pack.sha256,StringComparison.OrdinalIgnoreCase))throw new InvalidDataException("Language pack size or checksum differs from its catalog.");
   string temp=Path.Combine(Path.GetTempPath(),"AetherfallLanguages",Guid.NewGuid().ToString("N"));Directory.CreateDirectory(temp);
   string backupRoot=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Aetherfall","LanguageBackups");
   string settings=At(root,"Mods/Languages/settings.json");if(File.Exists(settings)){var values=Json.Deserialize<Dictionary<string,string>>(File.ReadAllText(settings));if(values.ContainsKey("BackupRoot"))backupRoot=Path.GetFullPath(values["BackupRoot"]);}
   if(backupRoot.Equals(root,StringComparison.OrdinalIgnoreCase)||backupRoot.StartsWith(root+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new IOException("Language backups must be outside the client.");
   string receipt="Mods/Languages/Installed/"+pack.id+".json";NoLinks(root,receipt);
   LanguageDownload old=File.Exists(At(root,receipt))?Json.Deserialize<LanguageDownload>(File.ReadAllText(At(root,receipt))):null;
   var changes=new Dictionary<string,string>();var before=new Dictionary<string,string>();long total=pack.files.Sum(x=>x.bytes),done=0;int index=0;
   try {
    using(var zip=ZipFile.OpenRead(archive)) {
     var names=new HashSet<string>(StringComparer.OrdinalIgnoreCase);foreach(var entry in zip.Entries)if(!names.Add(entry.FullName))throw new InvalidDataException("Duplicate language ZIP entry.");
     if(zip.Entries.Count!=pack.files.Count+1||zip.GetEntry("language-pack.json")==null)throw new InvalidDataException("Language ZIP contains unexpected files.");
     foreach(var file in pack.files) {
      index++;NoLinks(root,file.path);var entry=zip.GetEntry("payload/"+file.path);if(entry==null||entry.Length!=file.bytes)throw new InvalidDataException("Missing language file: "+file.path);
      string staged=At(temp,file.path);Directory.CreateDirectory(Path.GetDirectoryName(staged));
      using(var source=entry.Open())using(var target=File.Create(staged))source.CopyTo(target);
      if(Hash(staged)!=file.sha256)throw new InvalidDataException("Language file checksum mismatch: "+file.path);
      string targetPath=At(root,file.path),hash=File.Exists(targetPath)?Hash(targetPath):null;
      if(hash!=null&&hash!=file.sha256&&(old==null||!old.files.Any(x=>x.path==file.path&&x.sha256==hash)))throw new IOException("An unowned or edited language file is already installed: "+file.path);
      if(hash!=file.sha256){changes.Add(file.path,staged);before.Add(file.path,hash);}
      done+=file.bytes;UpdateTransfer.Report("preparing","bytes",done,total,index,pack.files.Count,file.path,0,false);
     }
    }
    string stagedReceipt=At(temp,receipt);Directory.CreateDirectory(Path.GetDirectoryName(stagedReceipt));File.WriteAllText(stagedReceipt,Json.Serialize(pack));changes.Add(receipt,stagedReceipt);before.Add(receipt,File.Exists(At(root,receipt))?Hash(At(root,receipt)):null);
    Closed();string backup=Path.Combine(backupRoot,Guid.NewGuid().ToString("N"));Directory.CreateDirectory(backup);
    foreach(var item in changes)if(before[item.Key]!=null){string saved=At(backup,item.Key);Directory.CreateDirectory(Path.GetDirectoryName(saved));File.Copy(At(root,item.Key),saved);if(Hash(saved)!=before[item.Key])throw new IOException("Language backup checksum mismatch.");}
    var written=new List<string>();try {
     index=0;foreach(var item in changes) {
      Closed();NoLinks(root,item.Key);string target=At(root,item.Key);if((File.Exists(target)?Hash(target):null)!=before[item.Key])throw new IOException("Language destination changed during preparation.");
      Directory.CreateDirectory(Path.GetDirectoryName(target));written.Add(item.Key);File.Copy(item.Value,target,true);if(Hash(target)!=Hash(item.Value))throw new IOException("Copied language checksum mismatch.");
      UpdateTransfer.Report("installing","files",++index,changes.Count,index,changes.Count,item.Key,0,true);
     }
    }catch{foreach(string relative in written.AsEnumerable().Reverse()){string target=At(root,relative);if(before[relative]!=null)File.Copy(At(backup,relative),target,true);else if(File.Exists(target))File.Delete(target);}throw;}
   }finally{if(Directory.Exists(temp))Directory.Delete(temp,true);}
  }
 }
}
