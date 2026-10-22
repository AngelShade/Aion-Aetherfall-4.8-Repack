using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;

namespace Aetherfall {
 public enum ModKind { Interface, Switchable, Required, Fixed }
 public sealed class ModEntry {
  public string Name, Folder, Lock, Reason, Desired, Applied, Theme, AppliedTheme;
  public List<InterfaceTheme> Themes=new List<InterfaceTheme>();
  public ModInfo Info; public ModKind Kind;
  public List<string> Files=new List<string>(), Missing=new List<string>();
 }
 public sealed class ModSwitchState { public string Desired="on", Applied="on"; public Dictionary<string,string> Parked=new Dictionary<string,string>(); }

 // Reads every Mods/<name>/mod.json and decides how each mod may be switched. Only two
 // mechanisms change files: the Aetherfall Interface's recorded states, and mods that opt in
 // with "Switch":"files" (their loose files are parked in Mods/<name>/disabled). A DLL that
 // Game.dll or aion.bin imports is never moved: the client would not start without it.
 public static class ModCatalog {
  static readonly JavaScriptSerializer Json=new JavaScriptSerializer();
  static readonly string[] Hosts={"bin64/Game.dll","bin64/aion.bin"};
  static readonly string[] CoreExtensions={".pak",".sig",".key",".exe",".bin"};

  public static string ClientPath(string root,string relative) {
   string path=Path.GetFullPath(Path.Combine(root,relative));
   if(!path.StartsWith(root+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase))throw new InvalidDataException("A mod path is outside this client.");
   return path;
  }
  static string Normal(string relative) { return relative.Replace('\\','/').TrimStart('/'); }
  static string FolderTitle(string name) { return Regex.Replace(name,@"(?<=[a-z0-9])(?=[A-Z])"," "); }

  public static ModInfo ReadInfo(string folder) {
   string path=Path.Combine(folder,"mod.json"), title=FolderTitle(Path.GetFileName(folder));
   ModInfo info=null;bool changed=false, writable=true;
   try { if(File.Exists(path)) info=Json.Deserialize<ModInfo>(File.ReadAllText(path)); }
   catch(Exception error) {
    if(!(error is IOException || error is UnauthorizedAccessException || error is ArgumentException || error is InvalidOperationException))throw;writable=false;}
   if(info==null) { info=new ModInfo(); changed=!File.Exists(path); }
   if(String.IsNullOrWhiteSpace(info.Title)) { info.Title=title; changed=true; }
   if(String.IsNullOrWhiteSpace(info.Description)) {
    info.Description="Client features provided by "+title+".";
    foreach(string readme in new string[]{"README.txt","README.md"}) {
     string candidate=Path.Combine(folder,readme);
     if(!File.Exists(candidate))continue;
     try {
      string line=File.ReadLines(candidate).Select(x=>x.Trim()).FirstOrDefault(x=>x.Length>25 && !x.StartsWith("#") && !x.StartsWith("`") && !x.StartsWith("  "));
      if(line!=null)info.Description=line.Length>240?line.Substring(0,237)+"...":line;
     } catch(IOException) {} catch(UnauthorizedAccessException) {}
     break;
    }
    changed=true;
   }
   if(info.Files==null)info.Files=new string[0];
   if(changed && writable) try { File.WriteAllText(path,Json.Serialize(info)); } catch(IOException) {} catch(UnauthorizedAccessException) {}
   return info;
  }

  public static ModInventory Inventory(string root) {
   try { return Json.Deserialize<ModInventory>(File.ReadAllText(Path.Combine(root,"Mods","InstalledMods.json")))??new ModInventory(); }
   catch(Exception error) {
    if(!(error is IOException || error is UnauthorizedAccessException || error is ArgumentException || error is InvalidOperationException))throw;
    return new ModInventory();
   }
  }

  // DLL names (lower case) imported by the client's own modules, mapped to the importer.
  // Returns null when a module cannot be read; callers then treat every DLL as required.
  public static Dictionary<string,string> Imports(string root) {
   Dictionary<string,string> result=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
   try {
    foreach(string host in Hosts) {
     string path=Path.Combine(root,host);
     if(!File.Exists(path))continue;
     foreach(string name in PeImports(File.ReadAllBytes(path)))if(!result.ContainsKey(name))result[name]=Path.GetFileName(host);
    }
    return result;
   } catch(Exception error) {
    if(!(error is IOException || error is UnauthorizedAccessException || error is InvalidDataException || error is ArgumentException || error is IndexOutOfRangeException))throw;
    return null;
   }
  }
  static List<string> PeImports(byte[] d) {
   Func<int,int> u16=o=>BitConverter.ToUInt16(d,o); Func<int,int> u32=o=>BitConverter.ToInt32(d,o);
   int pe=u32(0x3c); if(pe<0||pe+24>d.Length||u32(pe)!=0x4550)throw new InvalidDataException("Not a PE module.");
   int sections=u16(pe+6), optional=pe+24, header=optional+u16(pe+20);
   int directory=optional+(u16(optional)==0x20b?112:96)+8, rva=u32(directory);
   Func<int,int> offset=r=>{
    for(int i=0;i<sections;i++){int s=header+i*40, vs=u32(s+8), va=u32(s+12), rs=u32(s+16), ro=u32(s+20);if(r>=va&&r<va+Math.Max(vs,rs))return r-va+ro;}
    throw new InvalidDataException("Import table outside the module.");
   };
   List<string> names=new List<string>();
   if(rva==0)return names;
   for(int at=offset(rva);u32(at+12)!=0;at+=20) {
    int name=offset(u32(at+12)), end=Array.IndexOf(d,(byte)0,name);
    if(end<0)throw new InvalidDataException("Unterminated import name.");
    names.Add(Encoding.ASCII.GetString(d,name,end-name));
   }
   return names;
  }

  public static List<ModEntry> Read(string root) {
   root=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
   ModInventory inventory=Inventory(root);
   Dictionary<string,string> imports=Imports(root);
   List<ModEntry> mods=new List<ModEntry>();
   foreach(string folder in Directory.GetDirectories(Path.Combine(root,"Mods")).OrderBy(x=>x,StringComparer.OrdinalIgnoreCase)) {
    string name=Path.GetFileName(folder);
    if(name.Equals("Backups",StringComparison.OrdinalIgnoreCase) || (File.GetAttributes(folder)&FileAttributes.ReparsePoint)!=0 || !File.Exists(Path.Combine(folder,"mod.json")))continue;
    ModEntry mod=new ModEntry { Name=name,Folder=folder,Info=ReadInfo(folder) };
    HashSet<string> files=new HashSet<string>(mod.Info.Files.Where(x=>!String.IsNullOrWhiteSpace(x)).Select(Normal),StringComparer.OrdinalIgnoreCase);
    List<InstalledFile> records;
    if(inventory.features!=null && inventory.features.TryGetValue(name,out records))
     foreach(InstalledFile file in records)if(file!=null && !String.IsNullOrWhiteSpace(file.path))files.Add(Normal(file.path));
    mod.Files=files.OrderBy(x=>x,StringComparer.OrdinalIgnoreCase).ToList();
    Classify(root,mod,imports,inventory,name);
    foreach(string relative in mod.Files) {
     try { if(!File.Exists(ClientPath(root,relative)) && !(mod.Kind==ModKind.Switchable && mod.Applied=="off"))mod.Missing.Add(relative); }
     catch(ArgumentException) { mod.Missing.Add("Invalid mod file path"); } catch(InvalidDataException) { mod.Missing.Add("Invalid mod file path"); }
    }
    if(mod.Files.Count==0 && !Directory.GetFiles(folder).Any(x=>!Path.GetFileName(x).Equals("mod.json",StringComparison.OrdinalIgnoreCase)
      && !Path.GetFileName(x).StartsWith("README",StringComparison.OrdinalIgnoreCase)) && Directory.GetDirectories(folder).Length==0)
     mod.Missing.Add("No mod files found");
    mods.Add(mod);
   }
   return mods;
  }

  static void Classify(string root,ModEntry mod,Dictionary<string,string> imports,ModInventory inventory,string name) {
   if(name.Equals("AetherfallUI",StringComparison.OrdinalIgnoreCase) && AetherfallInterface.Installed(root)) {
    mod.Kind=ModKind.Interface;
    try {
     InterfaceState state=AetherfallInterface.ReadState(root); InterfaceManifest manifest=AetherfallInterface.ReadManifest(root);
     mod.Desired=state.Desired; mod.Applied=state.Applied; mod.Themes=AetherfallInterface.Themes(manifest);
     mod.Theme=AetherfallInterface.Theme(manifest,state.Theme); mod.AppliedTheme=AetherfallInterface.AppliedTheme(manifest,state);
    }
    catch(Exception error) { if(!(error is IOException||error is ArgumentException||error is InvalidOperationException))throw; mod.Desired="on"; }
    return;
   }
   foreach(string relative in mod.Files) {
    string file=Path.GetFileName(relative), host;
    if(!file.EndsWith(".dll",StringComparison.OrdinalIgnoreCase))continue;
    if(imports==null) { Lock(mod,"REQUIRED BY THE CLIENT","The client's import tables could not be read, so its DLLs stay in place."); return; }
    if(imports.TryGetValue(file,out host)) { Lock(mod,"REQUIRED BY "+host.ToUpperInvariant(),host+" loads "+file+" when the game starts. Removing it would stop Aion from starting."); return; }
   }
   string part=Hosts.FirstOrDefault(h=>mod.Files.Any(x=>String.Equals(x,h,StringComparison.OrdinalIgnoreCase)));
   if(part!=null) { Lock(mod,"PART OF "+Path.GetFileName(part).ToUpperInvariant(),"Built into "+Path.GetFileName(part)+". It is removed only by its own restore script."); return; }
   string reason=String.Equals(mod.Info.Switch,"files",StringComparison.OrdinalIgnoreCase)?SwitchProblem(root,mod,inventory):"Installed into the game's files; it has no launcher switch.";
   if(reason!=null) { mod.Kind=ModKind.Fixed; mod.Lock="ALWAYS ON"; mod.Reason=reason; return; }
   ModSwitchState saved=ReadSwitch(mod.Folder); mod.Kind=ModKind.Switchable; mod.Desired=saved.Desired; mod.Applied=saved.Applied;
  }
  static void Lock(ModEntry mod,string label,string reason) { mod.Kind=ModKind.Required; mod.Lock=label; mod.Reason=reason; }

  // Loose files outside Mods/ are the ones a switch parks. Archives, signatures, keys and
  // executables are shared game data; a file another mod also records is never moved.
  public static List<string> Movable(ModEntry mod) { return mod.Files.Where(x=>!x.StartsWith("Mods/",StringComparison.OrdinalIgnoreCase)).ToList(); }
  static string SwitchProblem(string root,ModEntry mod,ModInventory inventory) {
   List<string> movable=Movable(mod);
   if(movable.Count==0)return "Its mod.json lists no files outside Mods to switch.";
   foreach(string relative in movable) {
    try { ClientPath(root,relative); } catch(InvalidDataException) { return "A listed file is outside this client."; }
    if(CoreExtensions.Contains(Path.GetExtension(relative).ToLowerInvariant()))return relative+" is shared game data; this mod stays on.";
    foreach(var other in inventory.features??new Dictionary<string,List<InstalledFile>>()) {
     if(other.Key.Equals(mod.Name,StringComparison.OrdinalIgnoreCase)||other.Value==null)continue;
     if(other.Value.Any(x=>x!=null&&String.Equals(Normal(x.path??""),relative,StringComparison.OrdinalIgnoreCase)))return relative+" is shared with "+other.Key+".";
    }
   }
   return null;
  }

  static string SwitchPath(string folder) { return Path.Combine(folder,"state.json"); }
  public static ModSwitchState ReadSwitch(string folder) {
   string path=SwitchPath(folder);
   ModSwitchState state=File.Exists(path)?Json.Deserialize<ModSwitchState>(File.ReadAllText(path))??new ModSwitchState():new ModSwitchState();
   if(state.Desired!="off")state.Desired="on";
   if(state.Applied!="off")state.Applied="on";
   if(state.Parked==null)state.Parked=new Dictionary<string,string>();
   return state;
  }
  static void SaveSwitch(string folder,ModSwitchState state) {
   string path=SwitchPath(folder),temp=path+".new";File.WriteAllText(temp,Json.Serialize(state),new UTF8Encoding(false));
   if(File.Exists(path))File.Replace(temp,path,null);else File.Move(temp,path);
  }
  public static void SetTheme(string root,ModEntry mod,string theme) {
   if(mod.Kind!=ModKind.Interface)throw new InvalidOperationException(mod.Info.Title+" has no colour themes.");
   AetherfallInterface.SetTheme(root,theme); mod.Theme=theme;
  }
  public static void SetDesired(string root,ModEntry mod,string desired) {
   if(mod.Kind==ModKind.Interface) { AetherfallInterface.SetDesired(root,desired); mod.Desired=desired; return; }
   if(mod.Kind!=ModKind.Switchable)throw new InvalidOperationException(mod.Info.Title+" cannot be switched from the launcher.");
   if(desired!="on"&&desired!="off")throw new ArgumentException("Unknown mod state: "+desired);
   ModSwitchState state=ReadSwitch(mod.Folder); state.Desired=desired; SaveSwitch(mod.Folder,state); mod.Desired=desired;
  }

  static bool Running() {
   foreach(string name in new string[]{"aion","aion.bin"})foreach(Process process in Process.GetProcessesByName(name))using(process){if(!process.HasExited)return true;}
   return false;
  }
  static string Hash(string path) { return CustomQuests.Hash(File.ReadAllBytes(path)); }
  static void Move(string from,string to) { Directory.CreateDirectory(Path.GetDirectoryName(to)); File.Move(from,to); }

  // Applies every "Switch":"files" mod's desired state while Aion is closed. Each mod is
  // all-or-nothing: a failed move returns its files to where they were.
  public static string Apply(string root) {
   root=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
   List<ModEntry> mods=Read(root).Where(x=>x.Kind==ModKind.Switchable).ToList();
   ModInventory inventory=Inventory(root);
   List<string> lines=new List<string>();
   foreach(ModEntry mod in mods) {
    ModSwitchState state=ReadSwitch(mod.Folder);
    if(state.Desired==state.Applied) { lines.Add(mod.Info.Title+" unchanged ("+state.Desired+")."); continue; }
    if(Running()) { lines.Add(mod.Info.Title+": Aion is running; it switches when every client is closed."); continue; }
    List<string[]> done=new List<string[]>();
    try {
     if(state.Desired=="off") {
      List<InstalledFile> records=null; if(inventory.features!=null)inventory.features.TryGetValue(mod.Name,out records);
      foreach(string relative in Movable(mod)) {
       string live=ClientPath(root,relative), parked=ClientPath(root,"Mods/"+mod.Name+"/disabled/"+relative);
       if(!File.Exists(live))throw new FileNotFoundException(relative+" is missing; nothing was switched.");
       if(File.Exists(parked))throw new IOException("A disabled copy of "+relative+" already exists; nothing was switched.");
       string hash=Hash(live);
       InstalledFile record=records==null?null:records.FirstOrDefault(x=>x!=null&&String.Equals(Normal(x.path??""),relative,StringComparison.OrdinalIgnoreCase));
       if(record!=null&&!String.IsNullOrEmpty(record.installedSha256)&&!String.Equals(record.installedSha256,hash,StringComparison.OrdinalIgnoreCase))
        throw new InvalidDataException(relative+" changed since it was installed; nothing was switched.");
       if(Running())throw new InvalidOperationException("Aion started while "+mod.Info.Title+" was switching.");
       Move(live,parked); done.Add(new string[]{parked,live}); state.Parked[relative]=hash;
      }
     } else {
      foreach(var item in state.Parked) {
       string live=ClientPath(root,item.Key), parked=ClientPath(root,"Mods/"+mod.Name+"/disabled/"+item.Key);
       if(File.Exists(live))throw new IOException(item.Key+" was installed again while the mod was off; nothing was switched.");
       if(!File.Exists(parked)||Hash(parked)!=item.Value)throw new InvalidDataException("The disabled copy of "+item.Key+" is missing or changed; nothing was switched.");
       if(Running())throw new InvalidOperationException("Aion started while "+mod.Info.Title+" was switching.");
       Move(parked,live); done.Add(new string[]{live,parked});
      }
      state.Parked.Clear();
     }
     state.Applied=state.Desired; SaveSwitch(mod.Folder,state);
     lines.Add(mod.Info.Title+" "+state.Applied+": "+done.Count+" file(s) moved.");
    } catch(Exception error) {
     if(!(error is IOException||error is UnauthorizedAccessException||error is InvalidDataException||error is InvalidOperationException))throw;
     for(int i=done.Count-1;i>=0;i--)Move(done[i][0],done[i][1]);
     lines.Add(mod.Info.Title+" was not switched: "+error.Message);
    }
   }
   return lines.Count==0?"No switchable file mods.":String.Join("\r\n",lines);
  }
 }
}
