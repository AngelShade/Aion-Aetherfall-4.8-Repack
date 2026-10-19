using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Web.Script.Serialization;

namespace Aetherfall {
 public sealed class InterfaceEntry { public string off, on, layout; }
 public sealed class InterfaceArchive { public Dictionary<string,InterfaceEntry> entries=new Dictionary<string,InterfaceEntry>(); }
 public sealed class InterfaceManifest {
  public string name, version;
  public Dictionary<string,InterfaceArchive> archives=new Dictionary<string,InterfaceArchive>();
  public Dictionary<string,InterfaceEntry> files=new Dictionary<string,InterfaceEntry>();
 }
 public sealed class InterfaceStamp { public long Length, Ticks; }
 public sealed class InterfaceState {
  public string Desired="on", Applied;
  public Dictionary<string,InterfaceStamp> Stamps=new Dictionary<string,InterfaceStamp>();
  public InterfaceStamp Graphics;
 }

 // Aetherfall Interface: switches the addon's own archive entries and loose files between
 // the recorded off/on/layout payloads. Entries owned by other mods are never touched; a
 // managed entry whose current content matches no recorded state blocks the switch.
 public static class AetherfallInterface {
  public const string Folder="Mods/AetherfallUI";
  static readonly JavaScriptSerializer Json=new JavaScriptSerializer { MaxJsonLength=64*1024*1024 };
  static readonly UTF8Encoding Utf8=new UTF8Encoding(false,true);
  static readonly string[] States={"off","on","layout"};

  public static bool Installed(string root) { return File.Exists(Path.Combine(root,Folder,"manifest.json")); }
  static string StatePath(string root) { return Path.Combine(root,Folder,"state.json"); }
  public static InterfaceState ReadState(string root) {
   string path=StatePath(root);
   if(!File.Exists(path))return new InterfaceState();
   InterfaceState state=Json.Deserialize<InterfaceState>(File.ReadAllText(path,Utf8))??new InterfaceState();
   if(!States.Contains(state.Desired))state.Desired="on";
   if(state.Stamps==null)state.Stamps=new Dictionary<string,InterfaceStamp>();
   return state;
  }
  static void SaveState(string root,InterfaceState state) {
   string path=StatePath(root),temp=path+".new";File.WriteAllText(temp,Json.Serialize(state),Utf8);
   if(File.Exists(path))File.Replace(temp,path,null);else File.Move(temp,path);
  }
  public static void SetDesired(string root,string desired) {
   if(!States.Contains(desired))throw new ArgumentException("Unknown interface state: "+desired);
   InterfaceState state=ReadState(root);state.Desired=desired;SaveState(root,state);
  }
  static string Want(InterfaceEntry entry,string state) { return state=="off"?entry.off:state=="on"?entry.on:entry.layout; }
  static bool Known(InterfaceEntry entry,string hash) { return hash==entry.off||hash==entry.on||hash==entry.layout; }
  static byte[] Payload(string root,string hash) {
   string path=Path.Combine(root,Folder,"payloads",hash+".gz");
   if(!File.Exists(path))throw new FileNotFoundException("Aetherfall Interface payload is missing.",path);
   using(FileStream file=File.OpenRead(path))using(GZipStream gzip=new GZipStream(file,CompressionMode.Decompress))using(MemoryStream plain=new MemoryStream()){
    gzip.CopyTo(plain);byte[] bytes=plain.ToArray();
    if(CustomQuests.Hash(bytes)!=hash)throw new InvalidDataException("Aetherfall Interface payload is damaged: "+hash);
    return bytes;
   }
  }
  static bool Stamped(string root,InterfaceState state,IEnumerable<string> paths) {
   foreach(string relative in paths) {
    InterfaceStamp stamp;FileInfo file=new FileInfo(Path.Combine(root,relative));
    if(!state.Stamps.TryGetValue(relative,out stamp)||!file.Exists||file.Length!=stamp.Length||file.LastWriteTimeUtc.Ticks!=stamp.Ticks)return false;
   }
   return true;
  }
  static bool Running() {
   foreach(string name in new string[]{"aion","aion.bin"})foreach(Process process in Process.GetProcessesByName(name))using(process){if(!process.HasExited)return true;}
   return false;
  }
  static void Replace(string target,byte[] bytes) {
   string temp=target+".aetherfall-new";File.WriteAllBytes(temp,bytes);
   if(CustomQuests.Hash(File.ReadAllBytes(temp))!=CustomQuests.Hash(bytes)){File.Delete(temp);throw new IOException("Staged interface file failed verification: "+target);}
   File.Replace(temp,target,null);
  }

  static InterfaceStamp Stamp(string path) { FileInfo file=new FileInfo(path);return file.Exists?new InterfaceStamp { Length=file.Length,Ticks=file.LastWriteTimeUtc.Ticks }:null; }
  static InterfaceStamp GraphicsStamp(string root) { return Stamp(Path.Combine(root,GraphicsState)); }
  static bool SameStamp(InterfaceStamp a,InterfaceStamp b) { return a==null?b==null:b!=null&&a.Length==b.Length&&a.Ticks==b.Ticks; }
  const string GraphicsState="Mods/GraphicsMenu/installed.json",GraphicsManifest="Mods/GraphicsMenu/package/manifest.json",GraphicsPackage="Mods/GraphicsMenu/package";
  static void WriteText(string path,string text) { string temp=path+".aetherfall-new";File.WriteAllText(temp,text,Utf8);File.Replace(temp,path,null); }

  // The Graphics menu records exact hashes of game.pak and data.pak and keeps installed copies
  // in its package, like every mod composed with it. When a live archive differs from that
  // record only in Aetherfall Interface entries (each in a recorded state), the record and the
  // package copy follow the live archive. Any other difference is left for its own guard.
  // The Graphics restore baseline is not changed: it holds these entries in their off state.
  static string SyncGraphicsMenu(string root,InterfaceManifest manifest) {
   string statePath=Path.Combine(root,GraphicsState),manifestPath=Path.Combine(root,GraphicsManifest);
   if(!File.Exists(statePath)||!File.Exists(manifestPath))return null;
   Dictionary<string,object> state=Json.Deserialize<Dictionary<string,object>>(File.ReadAllText(statePath)),package=Json.Deserialize<Dictionary<string,object>>(File.ReadAllText(manifestPath));
   if(state.ContainsKey("restoredAt")&&state["restoredAt"]!=null)return null;
   System.Collections.ArrayList stateFiles=state["files"] as System.Collections.ArrayList,packageFiles=package["files"] as System.Collections.ArrayList;
   if(stateFiles==null||packageFiles==null||Json.Serialize(stateFiles)!=Json.Serialize(packageFiles))return "Graphics menu records differ; not synchronized.";
   List<string> rebased=new List<string>();
   for(int i=0;i<packageFiles.Count;i++) {
    Dictionary<string,object> entry=(Dictionary<string,object>)packageFiles[i];string relative=Convert.ToString(entry["path"]),tracked=Convert.ToString(entry["installed"]).ToLowerInvariant();
    string managed=manifest.archives.Keys.FirstOrDefault(k=>String.Equals(k,relative,StringComparison.OrdinalIgnoreCase));
    if(managed==null)continue;
    byte[] live=File.ReadAllBytes(Path.Combine(root,relative));string liveHash=CustomQuests.Hash(live);
    if(liveHash==tracked)continue;
    string copyPath=Path.Combine(root,GraphicsPackage,relative);byte[] copy=File.ReadAllBytes(copyPath);
    if(CustomQuests.Hash(copy)!=tracked)return "Graphics menu package copy of "+relative+" changed; not synchronized.";
    QuestPak a=new QuestPak(live),b=new QuestPak(copy);Dictionary<string,InterfaceEntry> entries=manifest.archives[managed].entries;
    if(!a.Names.SequenceEqual(b.Names))return "Graphics menu copy of "+relative+" has other entries; not synchronized.";
    foreach(string name in a.Names) {
     InterfaceEntry owned;byte[] x=a.Read(name),y=b.Read(name);
     if(entries.TryGetValue(name,out owned)) { if(!Known(owned,CustomQuests.Hash(x))||!Known(owned,CustomQuests.Hash(y)))return "Graphics menu copy of "+relative+" differs in "+name+"; not synchronized."; }
     else if(!x.SequenceEqual(y))return "Graphics menu copy of "+relative+" differs outside the interface ("+name+"); not synchronized.";
    }
    Replace(copyPath,live);
    entry["installed"]=liveHash;((Dictionary<string,object>)stateFiles[i])["installed"]=liveHash;rebased.Add(relative);
   }
   if(rebased.Count==0)return null;
   state["rebasedAt"]=DateTime.Now.ToString("o");
   WriteText(manifestPath,Json.Serialize(package));WriteText(statePath,Json.Serialize(state));
   return "Graphics menu record follows: "+String.Join(", ",rebased)+".";
  }

  // Applies the desired state. Returns a log line; throws only for damaged or foreign data.
  public static string Apply(string root) { return Apply(root,null); }
  public static string Apply(string root,string requested) {
   root=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
   if(!Installed(root))return "Aetherfall Interface is not installed.";
   InterfaceManifest manifest=Json.Deserialize<InterfaceManifest>(File.ReadAllText(Path.Combine(root,Folder,"manifest.json"),Utf8));
   InterfaceState state=ReadState(root);
   if(requested!=null){if(!States.Contains(requested))throw new ArgumentException("Unknown interface state: "+requested);state.Desired=requested;}
   string desired=state.Desired;
   List<string> paths=manifest.archives.Keys.Concat(manifest.files.Keys).ToList();
   if(state.Applied==desired&&Stamped(root,state,paths)&&SameStamp(state.Graphics,GraphicsStamp(root))){if(requested!=null)SaveState(root,state);return "Aetherfall Interface unchanged ("+desired+").";}
   if(Running()){SaveState(root,state);return "Aion is running; Aetherfall Interface stays as installed until every client is closed.";}

   Dictionary<string,byte[]> writes=new Dictionary<string,byte[]>();int entries=0;
   foreach(var archive in manifest.archives) {
    string target=Path.Combine(root,archive.Key);
    QuestPak pak=new QuestPak(File.ReadAllBytes(target));Dictionary<string,byte[]> changes=new Dictionary<string,byte[]>();
    foreach(var item in archive.Value.entries) {
     if(!pak.Contains(item.Key))throw new InvalidDataException("Aetherfall Interface entry is missing from "+archive.Key+": "+item.Key+". Rebuild the addon.");
     string current=CustomQuests.Hash(pak.Read(item.Key)),wanted=Want(item.Value,desired);
     if(current==wanted)continue;
     if(!Known(item.Value,current))throw new InvalidDataException("Another mod changed "+item.Key+" in "+archive.Key+". Rebuild Aetherfall Interface before switching it.");
     changes.Add(item.Key,Payload(root,wanted));
    }
    if(changes.Count>0){writes.Add(archive.Key,pak.Build(changes));entries+=changes.Count;}
   }
   foreach(var item in manifest.files) {
    string target=Path.Combine(root,item.Key);
    if(!File.Exists(target))throw new FileNotFoundException("Aetherfall Interface file is missing.",target);
    string current=CustomQuests.Hash(File.ReadAllBytes(target)),wanted=Want(item.Value,desired);
    if(current==wanted)continue;
    if(!Known(item.Value,current))throw new InvalidDataException("Another mod changed "+item.Key+". Rebuild Aetherfall Interface before switching it.");
    writes.Add(item.Key,Payload(root,wanted));entries++;
   }
   Dictionary<string,byte[]> before=writes.Keys.ToDictionary(x=>x,x=>File.ReadAllBytes(Path.Combine(root,x)));
   List<string> done=new List<string>();
   try {
    foreach(var write in writes) {
     if(Running())throw new InvalidOperationException("Aion started while the interface was switching.");
     Replace(Path.Combine(root,write.Key),write.Value);done.Add(write.Key);
    }
   } catch { foreach(string relative in done)Replace(Path.Combine(root,relative),before[relative]);throw; }
   string graphics=SyncGraphicsMenu(root,manifest);
   state.Applied=desired;state.Stamps=new Dictionary<string,InterfaceStamp>();
   foreach(string relative in paths)state.Stamps[relative]=Stamp(Path.Combine(root,relative));
   state.Graphics=GraphicsStamp(root);
   SaveState(root,state);
   return "Aetherfall Interface "+desired+": "+entries+" item(s) switched in "+writes.Count+" file(s)."+(graphics==null?"":" "+graphics);
  }
 }
}
