using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Web.Script.Serialization;

namespace Aetherfall {
 public sealed class LanguageOption {
  public string Code { get;set; }
  public string Name { get;set; }
  public bool Available { get;set; }
  public string Description { get { return Available?"Ready to use":"Install this pack from the Hub library first."; } }
 }
 // Shared discovery and preferences contract for the original/repack Hub and game launcher.
 public static class LanguageSelection {
  static readonly JavaScriptSerializer Json=new JavaScriptSerializer();
  static readonly string[,] Names={{"enu","English"},{"kor","Korean / 한국어"},{"jpn","Japanese / 日本語"},{"deu","German / Deutsch"},{"fra","French / Français"},{"rus","Russian / Русский"},{"esn","Spanish / Español"},{"ita","Italian / Italiano"},{"plk","Polish / Polski"},{"trk","Turkish / Türkçe"},{"chs","Chinese (Simplified)"},{"cht","Chinese (Traditional)"},{"ptb","Portuguese / Português"}};
  static string At(string root,string relative) { return Path.Combine(root,relative.Replace('/',Path.DirectorySeparatorChar)); }
  static string Source(string root,string packs,string relative) { const string prefix="Mods/Languages/Packs/";return packs!=null&&relative.StartsWith(prefix,StringComparison.Ordinal)?At(packs,relative.Substring(prefix.Length)):At(root,relative); }
  static string Pack(string root,string code,string part,string packs) {
   string managed="Mods/Languages/Packs/"+code+"/"+part;
   return code=="fra"||File.Exists(Source(root,packs,managed))||Directory.Exists(Source(root,packs,managed))?managed:"L10N/"+code+"/"+part;
  }
  public static List<LanguageOption> Options(string root,bool voices) {
   var result=new List<LanguageOption>();
   for(int i=0;i<Names.GetLength(0);i++) {
    string code=Names[i,0],path=At(root,Pack(root,code,voices?"sounds":"data/data.pak",null));
    bool available=voices?Directory.Exists(path)&&Directory.GetFiles(path,"*.pak",SearchOption.AllDirectories).Length>0:File.Exists(path);
    if(code=="kor"&&!voices)available=File.Exists(At(root,"Data/Strings/Strings.pak"));
    result.Add(new LanguageOption { Code=code,Name=Names[i,1],Available=available });
   }
   return result;
  }
  public static void Validate(string root,string text,string voice,string packs=null) {
   foreach(string code in new[]{text,voice})if(!Enumerable.Range(0,Names.GetLength(0)).Any(i=>Names[i,0]==code))throw new ArgumentException("Unknown language: "+code);
   if(text!="kor"&&!File.Exists(Source(root,packs,Pack(root,text,"data/data.pak",packs))))throw new InvalidOperationException("Install the "+text+" text pack before selecting it.");
   if(text=="kor"&&!File.Exists(At(root,"Data/Strings/Strings.pak")))throw new FileNotFoundException("The native Korean string tables are missing.");
   string sounds=Source(root,packs,Pack(root,voice,"sounds",packs));
   if(!Directory.Exists(sounds)||Directory.GetFiles(sounds,"*.pak",SearchOption.AllDirectories).Length==0)throw new InvalidOperationException("Install the "+voice+" voice pack before selecting it.");
  }
  public static Dictionary<string,object> Preferences(string root) {
   string path=At(root,"Mods/Launcher/preferences.json");
   return File.Exists(path)?Json.Deserialize<Dictionary<string,object>>(File.ReadAllText(path))??new Dictionary<string,object>():new Dictionary<string,object>();
  }
  public static string Selected(Dictionary<string,object> values,string key) { object value;return values.TryGetValue(key,out value)&&value is string?(string)value:"enu"; }
  public static void SaveFields(string root,Dictionary<string,object> changes) {
   var values=Preferences(root);foreach(var pair in changes)values[pair.Key]=pair.Value;
   string path=At(root,"Mods/Launcher/preferences.json"),temp=path+"."+Guid.NewGuid().ToString("N")+".tmp";
   Directory.CreateDirectory(Path.GetDirectoryName(path));
   try { File.WriteAllText(temp,Json.Serialize(values));if(File.Exists(path))File.Replace(temp,path,null);else File.Move(temp,path); }
   finally { if(File.Exists(temp))File.Delete(temp); }
  }
 }
}
