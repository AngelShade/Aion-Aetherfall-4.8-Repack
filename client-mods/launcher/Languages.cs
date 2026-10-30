using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;
using System.Xml.Linq;

namespace Aetherfall {
 public sealed class LanguageStamp { public long Length, Ticks; public string Hash; }
 public sealed class LanguageState {
  public int Version=2; public string Text, Voice;
  public int Strings, DialoguePages, FallbackStrings, FallbackPages;
  public List<string> SkippedDialogues=new List<string>();
  public Dictionary<string,LanguageStamp> Inputs=new Dictionary<string,LanguageStamp>();
  public Dictionary<string,LanguageStamp> Outputs=new Dictionary<string,LanguageStamp>();
 }
 public sealed class LanguageResult { public string Locale, Summary; public LanguageState State; }

 // Compose a separate native locale from the CURRENT modded English resources.
 // Original archives, ownership/recovery images, connection and country settings stay intact.
 public static class Languages {
  // NA country 1 admits ENU, DEU and FRA only. The native executable rejects
  // invented names (and KOR for this country), silently falling back to ENU.
  // Reserve the otherwise absent FRA slot for composed resources; this never
  // changes the selected text/voice labels, country, server or English archives.
  public const string Folder="Mods/Languages", Locale="fra";
  const string Active="L10N/fra", LegacyActive="L10N/afl", English="L10N/enu/data/data.pak";
  static readonly UTF8Encoding Utf8=new UTF8Encoding(false,true);
  static readonly JavaScriptSerializer Json=new JavaScriptSerializer { MaxJsonLength=64*1024*1024 };
  static string PathAt(string root,string relative) { return Path.Combine(root,relative.Replace('/',Path.DirectorySeparatorChar)); }
  static string Source(string root,string packs,string relative) {
   string prefix=Folder+"/Packs/";
   return packs!=null&&relative.StartsWith(prefix,StringComparison.Ordinal)?PathAt(packs,relative.Substring(prefix.Length)):PathAt(root,relative);
  }
  static string Pack(string root,string code,string part,string packs) {
   string managed=Folder+"/Packs/"+code+"/"+part;
   if(Directory.Exists(Source(root,packs,managed))||File.Exists(Source(root,packs,managed)))return managed;
   if(code==Locale)return managed; // A composed FRA folder is not a French translation pack.
   return "L10N/"+code+"/"+part;
  }
  public static List<LanguageOption> Options(string root,bool voices) { return LanguageSelection.Options(root,voices); }
  public static void Validate(string root,string text,string voice,string packs=null) { LanguageSelection.Validate(root,text,voice,packs); }
  public static string Arguments(string arguments,string locale) {
   if(locale!="enu"&&locale!=Locale)throw new ArgumentException("Unsupported prepared locale.");
   string result=Regex.Replace(arguments,@"(?i)(?:^|\s)-lang:[^\s]+","").Trim();
   return result+" -lang:"+locale.ToUpperInvariant();
  }
  static LanguageStamp Stamp(string path) { FileInfo file=new FileInfo(path);return new LanguageStamp { Length=file.Length,Ticks=file.LastWriteTimeUtc.Ticks }; }
  static bool Same(LanguageStamp a,LanguageStamp b) { return a!=null&&b!=null&&a.Length==b.Length&&a.Ticks==b.Ticks; }
  static string HashFile(string path) { using(var hash=System.Security.Cryptography.SHA256.Create())using(var file=File.OpenRead(path))return BitConverter.ToString(hash.ComputeHash(file)).Replace("-","").ToLowerInvariant(); }
  static string Relative(string root,string file) { return file.Substring(root.TrimEnd(Path.DirectorySeparatorChar).Length+1).Replace('\\','/'); }
  static IEnumerable<string> Files(string root,string relative,string packs=null) {
   string path=Source(root,packs,relative);if(!Directory.Exists(path))return new string[0];
   if((File.GetAttributes(path)&FileAttributes.ReparsePoint)!=0)throw new IOException("Language folders must not be directory links.");
   List<string> files=new List<string>();
   foreach(string file in Directory.GetFiles(path)) {
    if((File.GetAttributes(file)&FileAttributes.ReparsePoint)!=0)throw new IOException("Language files must not be links.");
    files.Add(relative+"/"+Path.GetFileName(file));
   }
   foreach(string child in Directory.GetDirectories(path))files.AddRange(Files(root,relative+"/"+Path.GetFileName(child),packs));
   return files;
  }
  static List<string> Inputs(string root,string text,string voice,string packs) {
   List<string> files=new List<string> { English,"L10N/enu/enu.pak","L10N/enu/textures/textures.pak" };
   // Loose locale error strings/loading images belong to the current installation too.
   files.AddRange(Files(root,"L10N/enu/data/Strings").Where(x=>x.EndsWith(".xml",StringComparison.OrdinalIgnoreCase)));
   files.AddRange(Files(root,"L10N/enu/textures/Loading").Where(x=>x.EndsWith(".bmp",StringComparison.OrdinalIgnoreCase)));
   if(text=="kor") {
    files.AddRange(new string[]{"Data/Strings/Strings.pak","Data/fonts/fonts.pak","Data/ui/ui.pak"});
    files.AddRange(Files(root,"Data/Dialogs").Where(x=>x.EndsWith(".pak",StringComparison.OrdinalIgnoreCase)));
   } else if(text!="enu")files.Add(Pack(root,text,"data/data.pak",packs));
   files.AddRange(Files(root,Pack(root,voice,"sounds",packs),packs).Where(x=>x.EndsWith(".pak",StringComparison.OrdinalIgnoreCase)));
   // Video audio is baked into the movie; use the selected voice pack's videos when supplied.
   string movies=Pack(root,voice,"data/cutscene/movie",packs);
   if(!Directory.Exists(Source(root,packs,movies)))movies="L10N/enu/data/cutscene/movie";
   files.AddRange(Files(root,movies,packs).Where(x=>x.EndsWith(".bik",StringComparison.OrdinalIgnoreCase)));
   return files.Distinct(StringComparer.OrdinalIgnoreCase).OrderBy(x=>x,StringComparer.Ordinal).ToList();
  }
  static bool Current(string root,LanguageState state,List<string> inputs,string packs) {
   if(state==null||state.Version!=2||state.Inputs.Count!=inputs.Count)return false;
   foreach(string relative in inputs) { LanguageStamp stamp;if(!state.Inputs.TryGetValue(relative,out stamp)||!Same(stamp,Stamp(Source(root,packs,relative))))return false; }
   foreach(var item in state.Outputs)if(!File.Exists(PathAt(root,item.Key))||!Same(item.Value,Stamp(PathAt(root,item.Key))))return false;
   return true;
  }
  static void Closed() {
   foreach(string name in new string[]{"aion","aion.bin"})foreach(Process process in Process.GetProcessesByName(name))using(process)
    if(!process.HasExited)throw new InvalidOperationException("Close Aion normally before preparing a different language. An unchanged prepared language can be reused.");
  }
  static Dictionary<string,XElement> Rows(XElement tree) {
   Dictionary<string,XElement> result=new Dictionary<string,XElement>(StringComparer.OrdinalIgnoreCase);
   foreach(XElement row in tree.Elements()) { string name=(string)row.Element("name");if(name!=null&&!result.ContainsKey(name))result.Add(name,row); }
   return result;
  }
  static byte[] Strings(byte[] current,byte[] translation,LanguageState state) {
   XElement tree=QuestBinaryXml.Decode(current);var rows=Rows(QuestBinaryXml.Decode(translation));
   foreach(XElement row in tree.Elements()) {
    XElement native;string name=(string)row.Element("name"), body=(string)row.Element("body");
    // IDs and formatting metadata remain the NA client's. Keep custom keys and web destinations.
    if(name==null||!rows.TryGetValue(name,out native)||(string)native.Element("id")!=(string)row.Element("id")||row.Element("body")==null||native.Element("body")==null||Regex.IsMatch(body??"",@"(?i)https?://|\[%web:|\[%shoplink")) { state.FallbackStrings++;continue; }
    row.Element("body").ReplaceNodes(native.Element("body").Nodes().Select(Clone));state.Strings++;
   }
   return QuestBinaryXml.Encode(tree);
  }
  static XNode Clone(XNode node) { XElement element=node as XElement;if(element!=null)return new XElement(element);XText text=node as XText;if(text!=null)return new XText(text.Value);return new XComment(((XComment)node).Value); }
  static byte[] FontFaces(byte[] current,byte[] translation,HashSet<string> nativeFaces) {
   XElement tree=QuestBinaryXml.Decode(current),other=QuestBinaryXml.Decode(translation);
   var styles=other.Elements("fontshader").Where(x=>x.Attribute("name")!=null).GroupBy(x=>(string)x.Attribute("name")).ToDictionary(x=>x.Key,x=>x.First());
   XElement fallback;styles.TryGetValue("default",out fallback);
   foreach(XElement style in tree.Elements("fontshader")) {
    XElement wanted;if(!styles.TryGetValue((string)style.Attribute("name")??"",out wanted))wanted=fallback;
    XElement font=wanted==null?null:wanted.Element("font");if(font==null)continue;
    foreach(XElement native in style.Elements("font"))if(nativeFaces.Contains((string)native.Attribute("name")??""))foreach(string key in new[]{"name","editor_name"})if(font.Attribute(key)!=null)native.SetAttributeValue(key,(string)font.Attribute(key));
   }
   return QuestBinaryXml.Encode(tree);
  }
  static string Attribute(string text,string name) { Match match=Regex.Match(text,"(?is)\\b"+name+"\\s*=\\s*([\"'])(.*?)\\1");return match.Success?match.Groups[2].Value:null; }
  static string Commands(string text) {
   return String.Join("\n",Regex.Matches(text,"(?is)\\b(href|action|cmd|command|event)\\s*=\\s*([\"'])(.*?)\\2").Cast<Match>().Select(x=>x.Groups[1].Value.ToLowerInvariant()+"="+x.Groups[3].Value).OrderBy(x=>x,StringComparer.Ordinal));
  }
  static byte[] Dialogue(byte[] current,byte[] translation,string name,LanguageState state) {
   try {
    string original=CustomQuests.DecodeHtml(current,name), native=CustomQuests.DecodeHtml(translation,name);
    // The game accepts authored HTML inside Contents, including non-XML nesting.
    // Keep its bytes as authored; translate only named pages' visible content and Act labels.
    Regex pages=new Regex(@"(?is)(<HtmlPage\b[^>]*>)(.*?)(</HtmlPage>)"),contents=new Regex(@"(?is)(<Contents\b[^>]*>)(.*?)(</Contents>)"),acts=new Regex(@"(?is)(<Act\b[^>]*>)(.*?)(</Act>)");
    var translatedPages=new Dictionary<string,string>();
    foreach(Match page in pages.Matches(native)){string id=Attribute(page.Groups[1].Value,"name");if(id!=null&&!translatedPages.ContainsKey(id))translatedPages.Add(id,page.Groups[2].Value);}
    string result=pages.Replace(original,page=>{
     string id=Attribute(page.Groups[1].Value,"name"), other;
     if(id==null||!translatedPages.TryGetValue(id,out other)){state.FallbackPages++;return page.Value;}
     string body=contents.Replace(page.Groups[2].Value,content=>{
      Match translated=contents.Match(other);
      if(!translated.Success||Commands(content.Groups[2].Value)!=Commands(translated.Groups[2].Value)){state.FallbackPages++;return content.Value;}
      state.DialoguePages++;return content.Groups[1].Value+translated.Groups[2].Value+content.Groups[3].Value;
     });
     body=acts.Replace(body,act=>{
      string href=Attribute(act.Groups[1].Value,"href");if(href==null)return act.Value;
      Match translated=acts.Matches(other).Cast<Match>().FirstOrDefault(x=>Attribute(x.Groups[1].Value,"href")==href);
      return translated!=null&&Commands(act.Value)==Commands(translated.Value)?act.Groups[1].Value+translated.Groups[2].Value+act.Groups[3].Value:act.Value;
     });
     return page.Groups[1].Value+body+page.Groups[3].Value;
    });
    return result==original?current:CustomQuests.EncodeHtml(result,name);
   } catch(Exception error) {
    if(!(error is System.Xml.XmlException||error is InvalidDataException))throw;
    state.FallbackPages++;state.SkippedDialogues.Add(name+": "+error.Message);return current;
   }
  }
  static Dictionary<string,byte[]> Translations(string root,string text,QuestPak english,LanguageState state,string packs) {
   var changes=new Dictionary<string,byte[]>();
   if(text=="enu")return changes;
   if(text!="kor") {
    QuestPak source=new QuestPak(File.ReadAllBytes(Source(root,packs,Pack(root,text,"data/data.pak",packs))));
    var names=source.Names.ToDictionary(x=>x.ToLowerInvariant(),x=>x);
    foreach(string name in english.Names) {
     string translated;if(!names.TryGetValue(name.ToLowerInvariant(),out translated))continue;
     if(name.StartsWith("strings/",StringComparison.OrdinalIgnoreCase)&&name.EndsWith(".xml",StringComparison.OrdinalIgnoreCase))changes[name]=Strings(english.Read(name),source.Read(translated),state);
     else if(name.StartsWith("dialogs/",StringComparison.OrdinalIgnoreCase)&&name.EndsWith(".html",StringComparison.OrdinalIgnoreCase))changes[name]=Dialogue(english.Read(name),source.Read(translated),name,state);
     else if(name=="fonts/fontshader.xml")changes[name]=FontFaces(english.Read(name),source.Read(translated),new HashSet<string>(english.Names.Where(x=>x.StartsWith("fonts/",StringComparison.OrdinalIgnoreCase)&&x.EndsWith(".ttf",StringComparison.OrdinalIgnoreCase)).Select(Path.GetFileNameWithoutExtension),StringComparer.OrdinalIgnoreCase));
    }
    foreach(string name in source.Names.Where(x=>x.StartsWith("fonts/",StringComparison.OrdinalIgnoreCase)&&x.EndsWith(".ttf",StringComparison.OrdinalIgnoreCase)))changes[name]=source.Read(name);
   } else {
    QuestPak source=new QuestPak(File.ReadAllBytes(PathAt(root,"Data/Strings/Strings.pak")));var names=source.Names.ToDictionary(x=>x.ToLowerInvariant(),x=>x);
    int hangul=0;
    foreach(string name in english.Names.Where(x=>x.StartsWith("strings/",StringComparison.OrdinalIgnoreCase)&&x.EndsWith(".xml",StringComparison.OrdinalIgnoreCase))) {
     string translated;if(!names.TryGetValue(name.Substring(8).ToLowerInvariant(),out translated))continue;
     byte[] bytes=source.Read(translated);hangul+=Regex.Matches(QuestBinaryXml.Decode(bytes).Value,"[가-힣]").Count;
     changes[name]=Strings(english.Read(name),bytes,state);
    }
    if(hangul<10000)throw new InvalidDataException("The base tables are not the expected Korean localization.");
    foreach(string file in state.Inputs.Keys.Where(x=>x.StartsWith("Data/Dialogs/",StringComparison.OrdinalIgnoreCase))) {
     QuestPak dialogs=new QuestPak(File.ReadAllBytes(PathAt(root,file)));string prefix=file.Substring(5, file.LastIndexOf('/')-4);
     var translatedNames=dialogs.Names.ToDictionary(x=>(prefix+x).ToLowerInvariant(),x=>x);
     foreach(string name in english.Names.Where(x=>x.EndsWith(".html",StringComparison.OrdinalIgnoreCase))) {
      string translated;if(translatedNames.TryGetValue(name.ToLowerInvariant(),out translated))changes[name]=Dialogue(english.Read(name),dialogs.Read(translated),name,state);
     }
    }
    QuestPak fonts=new QuestPak(File.ReadAllBytes(PathAt(root,"Data/fonts/fonts.pak"))), ui=new QuestPak(File.ReadAllBytes(PathAt(root,"Data/ui/ui.pak")));
    foreach(string font in new string[]{"fontshader.xml","graphicchar.xml"})changes["fonts/"+font]=fonts.Read(font);
    changes["ui/ui_font.xml"]=ui.Read("ui_font.xml");
   }
   if(state.Strings<1000)throw new InvalidDataException("This text pack does not match the installed client's native string keys and IDs.");
   return changes;
  }
  static void Write(string root,string relative,byte[] bytes,LanguageState state) {
   string path=PathAt(root,relative);Directory.CreateDirectory(Path.GetDirectoryName(path));File.WriteAllBytes(path,bytes);
   LanguageStamp stamp=Stamp(path);stamp.Hash=CustomQuests.Hash(bytes);state.Outputs.Add(relative,stamp);
  }
  public static LanguageResult Prepare(string root,string text,string voice,string output=null,string packs=null) {
   root=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
   Validate(root,text,voice,packs);
   if(text=="enu"&&voice=="enu")return new LanguageResult { Locale="enu",Summary="English text and voices; original English resources." };
   string statePath=PathAt(root,Folder+"/state.json");LanguageState old=File.Exists(statePath)?Json.Deserialize<LanguageState>(File.ReadAllText(statePath,Utf8)):null;
   if(old!=null&&old.Outputs.Keys.Any(x=>(!x.StartsWith(Active+"/",StringComparison.Ordinal)&&!x.StartsWith(LegacyActive+"/",StringComparison.Ordinal))||x.Split('/').Any(y=>y==".."||y==".")||Path.IsPathRooted(x)))throw new InvalidDataException("Invalid generated language ownership record.");
   List<string> inputs=Inputs(root,text,voice,packs);
   if(output==null&&old!=null&&old.Text==text&&old.Voice==voice&&Current(root,old,inputs,packs))return new LanguageResult { Locale=Locale,Summary="Prepared language unchanged: "+text+" text / "+voice+" voices.",State=old };
   if(output==null)Closed();
   if(Directory.Exists(PathAt(root,Active))&&(old==null||old.Version!=2))throw new IOException("The FRA language slot is already in use. Preserve its resources before preparing composed languages.");
   if(old!=null)foreach(var item in old.Outputs) {
    string path=PathAt(root,item.Key);if(File.Exists(path)&&!Same(item.Value,Stamp(path))&&HashFile(path)!=item.Value.Hash)throw new IOException("A prepared language file was edited externally: "+item.Key);
   }
   string work=output==null?Path.Combine(Path.GetTempPath(),"AetherfallLanguages",Guid.NewGuid().ToString("N")):Path.GetFullPath(output);
   if(work.Equals(root,StringComparison.OrdinalIgnoreCase)||work.StartsWith(root+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new IOException("Language preparation needs an external output folder.");
   if(Directory.Exists(PathAt(work,Active)))throw new IOException("Use a fresh language output folder.");
   LanguageState state=new LanguageState { Text=text,Voice=voice };
   foreach(string relative in inputs)state.Inputs.Add(relative,Stamp(Source(root,packs,relative)));
   QuestPak english=new QuestPak(File.ReadAllBytes(PathAt(root,English)));var replacements=Translations(root,text,english,state,packs);
   Write(work,Active+"/data/data.pak",replacements.Count==0?File.ReadAllBytes(PathAt(root,English)):english.Build(replacements),state);
   Write(work,Active+"/fra.pak",File.ReadAllBytes(PathAt(root,"L10N/enu/enu.pak")),state);
   Write(work,Active+"/textures/textures.pak",File.ReadAllBytes(PathAt(root,"L10N/enu/textures/textures.pak")),state);
   string soundPrefix=Pack(root,voice,"sounds",packs)+"/";
   foreach(string relative in inputs.Where(x=>x.StartsWith(soundPrefix,StringComparison.OrdinalIgnoreCase)))Write(work,Active+"/sounds/"+relative.Substring(soundPrefix.Length),File.ReadAllBytes(Source(root,packs,relative)),state);
   foreach(string relative in inputs.Where(x=>x.EndsWith(".bik",StringComparison.OrdinalIgnoreCase)))Copy(work,Active+"/data/cutscene/movie/"+Path.GetFileName(relative),Source(root,packs,relative),state);
   foreach(string relative in inputs.Where(x=>x.StartsWith("L10N/enu/",StringComparison.OrdinalIgnoreCase)&&!x.EndsWith(".pak",StringComparison.OrdinalIgnoreCase)&&!x.EndsWith(".bik",StringComparison.OrdinalIgnoreCase)))Copy(work,Active+"/"+relative.Substring(9),Source(root,packs,relative),state);
   foreach(string relative in inputs)if(!Same(state.Inputs[relative],Stamp(Source(root,packs,relative))))throw new IOException("Language source changed during preparation: "+relative);
   Directory.CreateDirectory(Path.GetDirectoryName(PathAt(work,Folder+"/state.json")));File.WriteAllText(PathAt(work,Folder+"/state.json"),Json.Serialize(state),Utf8);
   if(output==null)Install(root,work,old,state);
   return new LanguageResult { Locale=Locale,Summary="Prepared "+text+" text / "+voice+" voices; translated "+state.Strings+" strings and "+state.DialoguePages+" dialogue pages. Custom/unmatched text stays English. Movies use the supplied voice pack when available, otherwise the original English videos.",State=state };
  }
  static void Copy(string work,string relative,string source,LanguageState state) {
   string target=PathAt(work,relative);Directory.CreateDirectory(Path.GetDirectoryName(target));File.Copy(source,target);var stamp=Stamp(target);stamp.Hash=HashFile(target);state.Outputs.Add(relative,stamp);
  }
  static void Install(string root,string work,LanguageState old,LanguageState state) {
   // Only generated locale files are replaced; retain every previous image for rollback.
   string backupRoot=Path.Combine(Path.GetTempPath(),"AetherfallLanguages","Backups"),settings=PathAt(root,Folder+"/settings.json");
   if(File.Exists(settings)) { var config=Json.Deserialize<Dictionary<string,string>>(File.ReadAllText(settings,Utf8));if(config!=null&&config.ContainsKey("BackupRoot"))backupRoot=Path.GetFullPath(config["BackupRoot"]); }
   if(backupRoot==root||backupRoot.StartsWith(root+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new IOException("Language backups must be outside the client.");
   string backup=Path.Combine(backupRoot,Guid.NewGuid().ToString("N"));Directory.CreateDirectory(backup);
   var files=state.Outputs.Keys.Concat(old==null?Enumerable.Empty<string>():old.Outputs.Keys.AsEnumerable()).Concat(new string[]{Folder+"/state.json"}).Distinct().ToList();
   foreach(string relative in files) {
    string target=PathAt(root,relative);if(!File.Exists(target))continue;
    if(relative!=Folder+"/state.json"&&(old==null||!old.Outputs.ContainsKey(relative)))throw new IOException("Unowned file in prepared language folder: "+relative);
    string saved=PathAt(backup,relative);Directory.CreateDirectory(Path.GetDirectoryName(saved));File.Copy(target,saved);
   }
   List<string> written=new List<string>();
   try {
    foreach(string relative in files) {
     Closed();string target=PathAt(root,relative),source=PathAt(work,relative);Directory.CreateDirectory(Path.GetDirectoryName(target));
     string saved=PathAt(backup,relative);if(File.Exists(target)!=File.Exists(saved)||(File.Exists(saved)&&HashFile(target)!=HashFile(saved)))throw new IOException("Language destination changed during preparation: "+relative);
     written.Add(relative);
     if(File.Exists(source)){File.Copy(source,target,true);File.SetLastWriteTimeUtc(target,File.GetLastWriteTimeUtc(source));if(HashFile(target)!=HashFile(source))throw new IOException("Language copied-file hash differs.");}
     else if(File.Exists(target))File.Delete(target);
    }
   } catch { foreach(string relative in written.AsEnumerable().Reverse()){string target=PathAt(root,relative),saved=PathAt(backup,relative);if(File.Exists(saved))File.Copy(saved,target,true);else if(File.Exists(target))File.Delete(target);}throw; }
   File.WriteAllText(Path.Combine(backup,"receipt.json"),Json.Serialize(new { Text=state.Text,Voice=state.Voice,Files=files,State=state }),Utf8);
  }
 }
}
