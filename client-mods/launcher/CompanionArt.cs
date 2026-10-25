using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;

namespace Aetherfall {
 public sealed class CompanionArtRecipe {
  public string[] archives; public Dictionary<string,object[]> sprites;
 }

 // Derived metadata follows its two source archives. The bridge's exact archive
 // fingerprints, decompression CRCs and sprite bounds remain mandatory.
 public static class CompanionArt {
  public const string IndexPath="bin64/AionUiArt.index";
  static readonly string[] Archives={"Textures/ui/ui.pak","Textures/ui/skill_bg/skill_bg.pak"};
  public static bool Installed(string root) { return File.Exists(Path.Combine(root,IndexPath)); }
  static void Fixed(BinaryWriter writer,string text,int length) {
   byte[] value=Encoding.ASCII.GetBytes(text);
   if(value.Length>=length)throw new InvalidDataException("Companion artwork name is too long: "+text);
   writer.Write(value);writer.Write(new byte[length-value.Length]);
  }
  public static byte[] Build(string root,IDictionary<string,byte[]> replacements) {
   CompanionArtRecipe recipe;
   using(Stream resource=Assembly.GetExecutingAssembly().GetManifestResourceStream("Aetherfall.CompanionArt.json")) {
    if(resource==null)throw new InvalidDataException("The companion artwork recipe is missing from the launcher.");
    using(StreamReader reader=new StreamReader(resource))recipe=new JavaScriptSerializer().Deserialize<CompanionArtRecipe>(reader.ReadToEnd());
   }
   if(recipe==null||recipe.archives==null||!recipe.archives.SequenceEqual(Archives)||recipe.sprites==null||recipe.sprites.Count==0||recipe.sprites.Count>256)
    throw new InvalidDataException("Unsupported companion artwork recipe.");
   Dictionary<string,byte[]> raw=new Dictionary<string,byte[]>();
   Dictionary<string,QuestPak> paks=new Dictionary<string,QuestPak>();
   Dictionary<string,byte[]> textures=new Dictionary<string,byte[]>();
   foreach(string archive in Archives) {
    byte[] bytes;
    if(replacements==null||!replacements.TryGetValue(archive,out bytes))bytes=File.ReadAllBytes(Path.Combine(root,archive));
    raw.Add(archive,bytes);paks.Add(archive,new QuestPak(bytes));
   }
   using(MemoryStream output=new MemoryStream())using(BinaryWriter writer=new BinaryWriter(output)) {
    writer.Write(Encoding.ASCII.GetBytes("AIART001"));writer.Write((uint)recipe.sprites.Count);
    foreach(string archive in Archives) {
     Fixed(writer,archive,64);writer.Write((ulong)raw[archive].LongLength);
     string hash=CustomQuests.Hash(raw[archive]);
     for(int n=0;n<hash.Length;n+=2)writer.Write(Convert.ToByte(hash.Substring(n,2),16));
    }
    foreach(var sprite in recipe.sprites) {
     if(!Regex.IsMatch(sprite.Key,"^[a-z_-]{1,31}$")||sprite.Value.Length!=7)throw new InvalidDataException("Invalid companion sprite: "+sprite.Key);
     string archive=Convert.ToString(sprite.Value[0]),name=Convert.ToString(sprite.Value[1])+".dds";
     int archiveIndex=Array.IndexOf(Archives,archive);
     int x=Convert.ToInt32(sprite.Value[2]),y=Convert.ToInt32(sprite.Value[3]),w=Convert.ToInt32(sprite.Value[4]),h=Convert.ToInt32(sprite.Value[5]),transform=Convert.ToInt32(sprite.Value[6]);
     if(archiveIndex<0||x<0||y<0||w<=0||h<=0||x>65535||y>65535||w>65535||h>65535||transform<0||transform>2)
      throw new InvalidDataException("Unsupported companion sprite bounds: "+sprite.Key);
     QuestPak pak=paks[archive];string entry=pak.Names.FirstOrDefault(n=>String.Equals(n,name,StringComparison.OrdinalIgnoreCase));
     if(entry==null)throw new InvalidDataException("Companion artwork texture is missing: "+name);
     string key=archive+"/"+entry;byte[] dds;
     if(!textures.TryGetValue(key,out dds)){dds=pak.Read(entry);textures.Add(key,dds);}
     if(dds.Length<128||Encoding.ASCII.GetString(dds,0,4)!="DDS "||x+(long)w>BitConverter.ToUInt32(dds,16)||y+(long)h>BitConverter.ToUInt32(dds,12))
      throw new InvalidDataException("Companion artwork has invalid DDS bounds: "+name);
     QuestPakLocation location=pak.Locate(entry);
     if((location.Method!=0&&location.Method!=8)||location.Size>16*1024*1024||location.Compressed>16*1024*1024)
      throw new InvalidDataException("Unsupported companion texture encoding: "+name);
     Fixed(writer,sprite.Key,32);writer.Write((byte)archiveIndex);writer.Write((byte)transform);
     writer.Write((ushort)x);writer.Write((ushort)y);writer.Write((ushort)w);writer.Write((ushort)h);writer.Write(location.Method);
     writer.Write(location.Offset);writer.Write(location.Compressed);writer.Write(location.Size);writer.Write(location.Crc);
     writer.Write((ushort)location.Key.Length);writer.Write(location.Key);writer.Write(new byte[32-location.Key.Length]);
    }
    return output.ToArray();
   }
  }
  // Include the new index in the interface's existing write/rollback transaction.
  public static void Include(string root,Dictionary<string,byte[]> writes) {
   if(Installed(root)&&Archives.Any(a=>writes.ContainsKey(a)))writes.Add(IndexPath,Build(root,writes));
  }
  // Also runs after all launch preparation, catching external archive edits and a
  // missing theme dependency even when the interface's own state is unchanged.
  public static string Refresh(string root) {
   if(!Installed(root))return "Companion artwork index is not installed.";
   byte[] before=File.ReadAllBytes(Path.Combine(root,IndexPath)),expected=Build(root,null);
   if(before.SequenceEqual(expected))return "Companion artwork verified ("+BitConverter.ToUInt32(expected,8)+" sprites).";
   string path=Path.Combine(root,IndexPath),temp=path+".new";
   try {
    File.WriteAllBytes(temp,expected);
    if(!File.ReadAllBytes(temp).SequenceEqual(expected)||!File.ReadAllBytes(path).SequenceEqual(before))
     throw new IOException("Companion artwork index changed during preparation.");
    File.Replace(temp,path,null);
   } finally { if(File.Exists(temp))File.Delete(temp); }
   return "Companion artwork index refreshed for the current client archives.";
  }
 }
}
