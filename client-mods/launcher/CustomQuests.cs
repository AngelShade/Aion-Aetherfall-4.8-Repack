using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;
using System.Xml;
using System.Xml.Linq;

namespace Aetherfall {
 public sealed class QuestStamp { public long Length, Ticks; public string Hash; }
 public sealed class QuestInstallState {
  public int Version=1; public string Inputs;
  public Dictionary<string,string> Owned=new Dictionary<string,string>();
  public Dictionary<string,QuestStamp> Archives=new Dictionary<string,QuestStamp>();
 }
 public sealed class QuestPackage { public int Id; public string Title; }
 public sealed class QuestSettings { public string BackupRoot; }
 public static class CustomQuests {
  static readonly string[] Inputs={"quest.json","quest.xml","registration.xml","strings.xml","dialog.html","quest_script_monster.csv"};
  static readonly UTF8Encoding Utf8=new UTF8Encoding(false,true);
  static readonly JavaScriptSerializer Json=new JavaScriptSerializer { MaxJsonLength=16*1024*1024 };
  const string QuestArchive="Data/Quest/Quest.pak", EnglishArchive="L10N/ENU/data/data.pak";
  public static string Hash(byte[] bytes) { using(SHA256 sha=SHA256.Create()) return BitConverter.ToString(sha.ComputeHash(bytes)).Replace("-","").ToLowerInvariant(); }
  static string Fingerprint(XElement node) { return Hash(Utf8.GetBytes(node.ToString(SaveOptions.DisableFormatting))); }
  static XElement Xml(string text) {
   using(XmlReader reader=XmlReader.Create(new StringReader(text),new XmlReaderSettings { DtdProcessing=DtdProcessing.Prohibit,XmlResolver=null,IgnoreWhitespace=true })) return XElement.Load(reader);
  }
  static string Read(string path) { return File.ReadAllText(path,Utf8); }
  static string Required(XElement node,string field) { string value=(string)node.Element(field);if(String.IsNullOrWhiteSpace(value))throw new InvalidDataException("Custom quest is missing "+field+".");return value; }
  static List<string> Packages(string root) {
   List<string> result=new List<string>();
   foreach(string child in Directory.GetDirectories(root).OrderBy(x=>x,StringComparer.OrdinalIgnoreCase)) {
    if((File.GetAttributes(child)&FileAttributes.ReparsePoint)!=0)throw new InvalidDataException("Custom quest folders cannot be directory links.");
    if(File.Exists(Path.Combine(child,"quest.json")))result.Add(child);
    else result.AddRange(Packages(child));
   }
   return result;
  }
  static string InputHash(string folder,List<string> packages) {
   StringBuilder result=new StringBuilder();
   foreach(string package in packages)foreach(string name in Inputs) {
    string path=Path.Combine(package,name);result.Append(path.Substring(folder.Length).Replace('\\','/')).Append('\n').Append(Hash(File.ReadAllBytes(path))).Append('\n');
   }
   return Hash(Utf8.GetBytes(result.ToString()));
  }
  static bool Current(string root,QuestInstallState state) {
   foreach(string relative in new string[]{QuestArchive,EnglishArchive}) {
    QuestStamp stamp;FileInfo file=new FileInfo(Path.Combine(root,relative));
    if(!state.Archives.TryGetValue(relative,out stamp)||!file.Exists||file.Length!=stamp.Length||file.LastWriteTimeUtc.Ticks!=stamp.Ticks)return false;
   }
   return true;
  }
  static void Expect(QuestInstallState state,string key,string actual,bool adopt) {
   string expected;
   if(state.Owned.TryGetValue(key,out expected)) { if(expected!=actual)throw new InvalidDataException("Installed custom quest data changed outside its folder: "+key+". Reconcile the newer data before loading it."); }
   else if(!adopt&&actual!=null)throw new InvalidDataException("Custom quest conflicts with an existing client record: "+key+".");
  }
  static void Own(QuestInstallState next,string key,string value) {
   if(next.Owned.ContainsKey(key))throw new InvalidDataException("Two custom quest packages own the same record: "+key+".");next.Owned.Add(key,value);
  }
  static bool Node(XElement tree,XElement wanted,string key,QuestInstallState state,QuestInstallState next,bool adopt) {
   string id=Required(wanted,"id");List<XElement> matches=tree.Elements().Where(x=>(string)x.Element("id")==id).ToList();
   if(matches.Count>1)throw new InvalidDataException("Duplicate native quest record: "+key);
   XElement old=matches.SingleOrDefault();string actual=old==null?null:Fingerprint(old);Expect(state,key,actual,adopt);
   bool same=old!=null&&XNode.DeepEquals(old,wanted);
   if(adopt&&!same)throw new InvalidDataException("Installed quest does not match its packaged source: "+key);
   if(!same) { if(old==null)tree.Add(new XElement(wanted));else old.ReplaceWith(new XElement(wanted)); }
   Own(next,key,Fingerprint(wanted));return !same;
  }
  static List<string> Lines(string value) { return Regex.Matches(value,@"[^\r\n]*(?:\r\n|\n|\r|$)").Cast<Match>().Select(m=>m.Value).Where(s=>s.Length>0).ToList(); }
  static string Rows(string csv,int id) { string prefix=id.ToString()+",";return String.Concat(Lines(csv).Where(s=>s.StartsWith(prefix,StringComparison.Ordinal))); }
  static byte[] CryptHtml(byte[] input,string name) {
   string stem=Path.GetFileNameWithoutExtension(name);uint a=0,b=0;
   unchecked { for(int i=0;i<stem.Length;i++){uint v=(uint)((stem[i]&15)+i);a+=v;b^=v;}
    byte[] result=new byte[input.Length];for(int i=0;i<input.Length;i++){a=(a+0x1d)^b;b+=3;result[i]=(byte)(input[i]^(a&255));}return result; }
  }
  internal static string DecodeHtml(byte[] input,string name) {
   if(input.Length<4||input[0]!=0x81)throw new InvalidDataException("Expected native custom quest dialogue.");
   byte[] plain=CryptHtml(input.Skip(1).ToArray(),name);if(plain[0]!=0x81||plain[1]!=255||plain[2]!=254)throw new InvalidDataException("Custom quest dialogue could not be read.");
   return Encoding.Unicode.GetString(plain,3,plain.Length-3);
  }
  internal static byte[] EncodeHtml(string text,string name) {
   text=Regex.Replace(text,"encoding=\"UTF-8\"","encoding=\"UTF-16\"",RegexOptions.IgnoreCase);
   byte[] plain=new byte[]{0x81,255,254}.Concat(Encoding.Unicode.GetBytes(text)).ToArray();return new byte[]{0x81}.Concat(CryptHtml(plain,name)).ToArray();
  }
  static void Closed() {
   foreach(string name in new string[]{"aion","aion.bin"})foreach(Process process in Process.GetProcessesByName(name))using(process) {
    if(!process.HasExited)throw new InvalidOperationException("Close Aion normally before applying changed custom quests. Unchanged quests can be reused by additional clients.");
   }
  }
  static void SaveState(string path,QuestInstallState state) { string temp=path+".new";File.WriteAllText(temp,Json.Serialize(state),Utf8);if(File.Exists(path))File.Replace(temp,path,null);else File.Move(temp,path); }
  public static string Sync(string root,bool adopt) {
   root=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);string folder=Path.Combine(root,"Mods/CustomQuests"),statePath=Path.Combine(folder,"installed.json");
   List<string> packages=Packages(Path.Combine(folder,"Quests"));if(packages.Count==0)throw new InvalidDataException("No custom quest packages were found.");
   string inputHash=InputHash(folder,packages);QuestInstallState state=File.Exists(statePath)?Json.Deserialize<QuestInstallState>(Read(statePath)):new QuestInstallState();
   if(adopt&&File.Exists(statePath))throw new InvalidOperationException("Custom quests already have installation ownership records.");
   if(!adopt&&state.Inputs==inputHash&&Current(root,state))return "Custom quests unchanged ("+packages.Count+").";
   QuestInstallState next=new QuestInstallState { Inputs=inputHash };
   QuestPak quests=new QuestPak(File.ReadAllBytes(Path.Combine(root,QuestArchive))),english=new QuestPak(File.ReadAllBytes(Path.Combine(root,EnglishArchive)));
   XElement metadata=QuestBinaryXml.Decode(quests.Read("quest.xml")),registrations=QuestBinaryXml.Decode(quests.Read("data_driven_quest.xml")),strings=QuestBinaryXml.Decode(english.Read("strings/client_strings_quest.xml"));
   bool metaChanged=false,registrationChanged=false,stringsChanged=false;Dictionary<string,byte[]> questChanges=new Dictionary<string,byte[]>(),englishChanges=new Dictionary<string,byte[]>();
   string csv=Utf8.GetString(quests.Read("quest_script_monster.csv")),oldCsv=csv;HashSet<int> ids=new HashSet<int>();
   foreach(string package in packages) {
    QuestPackage info=Json.Deserialize<QuestPackage>(Read(Path.Combine(package,"quest.json")));if(info.Id<=0||!ids.Add(info.Id))throw new InvalidDataException("Custom quest IDs must be positive and unique.");
    string id=info.Id.ToString(),questName="Q"+id,dialogueName="quest_q"+id+".html",dialoguePath="dialogs/"+dialogueName;
    XElement q=Xml(Read(Path.Combine(package,"quest.xml"))),registration=Xml(Read(Path.Combine(package,"registration.xml"))),titles=Xml(Read(Path.Combine(package,"strings.xml")));
    if(q.Name!="quest"||Required(q,"id")!=id||Required(q,"name")!=questName||registration.Name!="quest_data_driven"||Required(registration,"id")!=id||Required(registration,"name")!=questName||titles.Name!="strings")throw new InvalidDataException("A custom quest package contains inconsistent IDs: "+package);
    metaChanged|=Node(metadata,q,"q:"+id,state,next,adopt);registrationChanged|=Node(registrations,registration,"r:"+id,state,next,adopt);
    foreach(XElement title in titles.Elements("string")) {
     string titleId=Required(title,"id"),name=Required(title,"name");
     if(strings.Elements().Any(x=>(string)x.Element("name")==name&&(string)x.Element("id")!=titleId))throw new InvalidDataException("Custom quest text key conflicts with another title: "+name);
     stringsChanged|=Node(strings,title,"s:"+titleId,state,next,adopt);
    }
    if(!titles.Elements().Any(x=>(string)x.Element("name")==Required(q,"desc")))throw new InvalidDataException("Custom quest title is missing.");
    string text=Read(Path.Combine(package,"dialog.html"));XElement authored=Xml(text);if(authored.Name!="HtmlPages")throw new InvalidDataException("Expected native HtmlPages for custom quest dialogue.");
    byte[] oldDialogue=english.Contains(dialoguePath)?english.Read(dialoguePath):null;string actual=oldDialogue==null?null:Hash(oldDialogue);Expect(state,"d:"+id,actual,adopt);
    bool same=oldDialogue!=null&&XNode.DeepEquals(Xml(DecodeHtml(oldDialogue,dialogueName)),authored);if(adopt&&!same)throw new InvalidDataException("Installed dialogue differs from its custom quest folder.");
    byte[] newDialogue=same?oldDialogue:EncodeHtml(text,dialogueName);if(!same)englishChanges.Add(dialoguePath,newDialogue);Own(next,"d:"+id,Hash(newDialogue));
    string wantedRows=Read(Path.Combine(package,"quest_script_monster.csv")),oldRows=Rows(csv,info.Id);if(Lines(wantedRows).Any(line=>!line.StartsWith(id+",",StringComparison.Ordinal)))throw new InvalidDataException("Custom quest monster guidance has another quest ID.");
    Expect(state,"c:"+id,oldRows.Length==0?null:Hash(Utf8.GetBytes(oldRows)),adopt);if(adopt&&wantedRows!=oldRows)throw new InvalidDataException("Installed custom quest monster guidance differs.");
    if(oldRows!=wantedRows)csv=String.Concat(Lines(csv).Where(line=>!line.StartsWith(id+",",StringComparison.Ordinal)))+wantedRows;
    Own(next,"c:"+id,wantedRows.Length==0?null:Hash(Utf8.GetBytes(wantedRows)));
   }
   if(state.Owned.Keys.Except(next.Owned.Keys).Any())throw new InvalidDataException("An installed custom quest source is missing. Restore its folder before loading more quests.");
   if(metaChanged)questChanges.Add("quest.xml",QuestBinaryXml.Encode(metadata));if(registrationChanged)questChanges.Add("data_driven_quest.xml",QuestBinaryXml.Encode(registrations));if(csv!=oldCsv)questChanges.Add("quest_script_monster.csv",Utf8.GetBytes(csv));if(stringsChanged)englishChanges.Add("strings/client_strings_quest.xml",QuestBinaryXml.Encode(strings));
   Dictionary<string,byte[]> writes=new Dictionary<string,byte[]>();if(questChanges.Count>0)writes.Add(QuestArchive,quests.Build(questChanges));if(englishChanges.Count>0)writes.Add(EnglishArchive,english.Build(englishChanges));
   if(adopt&&writes.Count>0)throw new InvalidDataException("Adoption must not replace native quest archives.");
   Dictionary<string,byte[]> before=new Dictionary<string,byte[]>();foreach(string relative in writes.Keys)before.Add(relative,File.ReadAllBytes(Path.Combine(root,relative)));
   if(writes.Count>0) {
    Closed();QuestSettings settings=Json.Deserialize<QuestSettings>(Read(Path.Combine(folder,"settings.json")));string backupRoot=Path.GetFullPath(settings.BackupRoot);
    if(backupRoot.StartsWith(root+Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase)||backupRoot==root)throw new InvalidDataException("Custom quest backups must be outside the client.");
    string backup=Path.Combine(backupRoot,DateTime.UtcNow.ToString("yyyyMMdd-HHmmss-fffffff"));Directory.CreateDirectory(backup);
    foreach(string relative in writes.Keys) { string saved=Path.Combine(backup,relative);Directory.CreateDirectory(Path.GetDirectoryName(saved));File.WriteAllBytes(saved,before[relative]); }
    List<string> copied=new List<string>();
    try { foreach(var item in writes) { Closed();string target=Path.Combine(root,item.Key);if(Hash(File.ReadAllBytes(target))!=Hash(before[item.Key]))throw new IOException("Quest archive changed during preparation.");copied.Add(item.Key);File.WriteAllBytes(target,item.Value);if(Hash(File.ReadAllBytes(target))!=Hash(item.Value))throw new IOException("Custom quest copied hash differs."); } }
    catch { foreach(string relative in copied)File.WriteAllBytes(Path.Combine(root,relative),before[relative]);throw; }
    File.WriteAllText(Path.Combine(backup,"receipt.json"),Json.Serialize(new { QuestIds=ids.ToArray(), Files=writes.Keys.ToArray(), Before=before.ToDictionary(x=>x.Key,x=>Hash(x.Value)), After=writes.ToDictionary(x=>x.Key,x=>Hash(x.Value)), ServerChanged=false }),Utf8);
   }
   foreach(string relative in new string[]{QuestArchive,EnglishArchive}) { FileInfo file=new FileInfo(Path.Combine(root,relative));next.Archives.Add(relative,new QuestStamp { Length=file.Length,Ticks=file.LastWriteTimeUtc.Ticks,Hash=Hash(File.ReadAllBytes(file.FullName)) }); }
   SaveState(statePath,next);return "Custom quests loaded ("+packages.Count+"); native archives changed: "+writes.Count+".";
  }
 }

 public static class QuestBinaryXml {
  static uint Int(BinaryReader reader) { uint value=0;int shift=0;byte b;do { b=reader.ReadByte();value|=(uint)(b&127)<<shift;shift+=7;if(shift>35)throw new InvalidDataException("Invalid packed XML integer."); }while((b&128)!=0);return value; }
  static void Int(BinaryWriter writer,uint value) { while(value>127){writer.Write((byte)((value&127)|128));value>>=7;}writer.Write((byte)value); }
  static string Text(byte[] data,uint offset) { int start=checked((int)offset*2),end=start;while(end+1<data.Length&&(data[end]!=0||data[end+1]!=0))end+=2;if(end+1>=data.Length)throw new InvalidDataException("Invalid native XML string.");return Encoding.Unicode.GetString(data,start,end-start); }
  static XElement Node(BinaryReader reader,byte[] strings) { XElement node=new XElement(Text(strings,Int(reader)));byte flags=reader.ReadByte();if((flags&1)!=0)node.Value=Text(strings,Int(reader));if((flags&2)!=0){uint count=Int(reader);for(uint i=0;i<count;i++)node.Add(new XAttribute(Text(strings,Int(reader)),Text(strings,Int(reader))));}if((flags&4)!=0){uint count=Int(reader);for(uint i=0;i<count;i++)node.Add(Node(reader,strings));}return node; }
  public static XElement Decode(byte[] bytes) { using(BinaryReader reader=new BinaryReader(new MemoryStream(bytes))){if(reader.ReadByte()!=128)throw new InvalidDataException("Expected native binary XML.");byte[] strings=reader.ReadBytes(checked((int)Int(reader)));return Node(reader,strings);} }
  public static byte[] Encode(XElement root) {
   MemoryStream strings=new MemoryStream();strings.WriteByte(0);strings.WriteByte(0);Dictionary<string,uint> indices=new Dictionary<string,uint>();indices.Add("",0);
   Func<string,uint> index=value=>{uint offset;if(indices.TryGetValue(value,out offset))return offset;offset=(uint)strings.Length/2;byte[] bytes=Encoding.Unicode.GetBytes(value);strings.Write(bytes,0,bytes.Length);strings.WriteByte(0);strings.WriteByte(0);indices.Add(value,offset);return offset;};
   MemoryStream nodes=new MemoryStream();BinaryWriter writer=new BinaryWriter(nodes);Action<XElement> encode=null;
   encode=node=>{Int(writer,index(node.Name.LocalName));string value=String.Concat(node.Nodes().OfType<XText>().Select(t=>t.Value));var attrs=node.Attributes().ToList();var children=node.Elements().ToList();writer.Write((byte)((value.Length>0?1:0)|(attrs.Count>0?2:0)|(children.Count>0?4:0)));if(value.Length>0)Int(writer,index(value));if(attrs.Count>0){Int(writer,(uint)attrs.Count);foreach(var a in attrs){Int(writer,index(a.Name.LocalName));Int(writer,index(a.Value));}}if(children.Count>0){Int(writer,(uint)children.Count);foreach(var child in children)encode(child);}};
   encode(root);MemoryStream resultStream=new MemoryStream();BinaryWriter output=new BinaryWriter(resultStream);output.Write((byte)128);Int(output,(uint)strings.Length);output.Write(strings.ToArray());output.Write(nodes.ToArray());byte[] result=resultStream.ToArray();if(!XNode.DeepEquals(root,Decode(result)))throw new InvalidDataException("Native XML roundtrip differs.");return result;
  }
 }

 public sealed class QuestPakLocation {
  public uint Offset, Compressed, Size, Crc; public ushort Method; public byte[] Key;
 }
 public sealed class QuestPak {
  sealed class Record { public string Name;public byte[] Local,Central;public int Header;public ushort Method;public uint Crc;public int Size,Length; }
  readonly List<Record> records=new List<Record>();readonly Dictionary<string,Record> byName=new Dictionary<string,Record>(StringComparer.Ordinal);readonly byte[] end;readonly int version;
  static ushort U16(byte[] b,int p){return BitConverter.ToUInt16(b,p);}static uint U32(byte[] b,int p){return BitConverter.ToUInt32(b,p);}static void Put(byte[] b,int p,uint n){Array.Copy(BitConverter.GetBytes(n),0,b,p,4);}static byte[] Slice(byte[] b,int p,int n){byte[] r=new byte[n];Array.Copy(b,p,r,0,n);return r;}
  static readonly uint[] CrcTable=MakeCrcTable();
  static uint[] MakeCrcTable(){uint[] table=new uint[256];for(uint i=0;i<256;i++){uint value=i;for(int j=0;j<8;j++)value=(value&1)!=0?(value>>1)^0xedb88320:value>>1;table[i]=value;}return table;}
  static uint Crc(byte[] bytes){uint crc=0xffffffff;foreach(byte b in bytes)crc=CrcTable[(crc^b)&255]^(crc>>8);return ~crc;}
  static byte[] Payload(Record r,int version) { byte[] data=Slice(r.Local,r.Header,r.Size);byte[] key=QuestPakKeys.Keys[version-1];int offset=version==1?(r.Size&31)*32:r.Size&1023;for(int i=0;i<Math.Min(32,data.Length);i++)data[i]^=key[offset+i];if(r.Method==0)return data;if(r.Method!=8)throw new InvalidDataException("Unsupported quest archive compression.");using(DeflateStream deflate=new DeflateStream(new MemoryStream(data),CompressionMode.Decompress))using(MemoryStream plain=new MemoryStream()){deflate.CopyTo(plain);return plain.ToArray();} }
  public QuestPak(byte[] raw) {
   int pos=0;while(U32(raw,pos)==0xfbfcb4af) { if((U16(raw,pos+6)&8)!=0)throw new InvalidDataException("Quest archive descriptors are unsupported.");int header=30+U16(raw,pos+26)+U16(raw,pos+28),size=checked((int)U32(raw,pos+18));string name=Encoding.UTF8.GetString(raw,pos+30,U16(raw,pos+26));if(byName.ContainsKey(name))throw new InvalidDataException("Duplicate quest archive member.");Record record=new Record { Name=name,Local=Slice(raw,pos,header+size),Header=header,Method=U16(raw,pos+8),Crc=U32(raw,pos+14),Size=size,Length=checked((int)U32(raw,pos+22)) };records.Add(record);byName.Add(name,record);pos+=header+size; }
   int centralCount=0;while(U32(raw,pos)==0xfdfeb4af){int length=46+U16(raw,pos+28)+U16(raw,pos+30)+U16(raw,pos+32);string name=Encoding.UTF8.GetString(raw,pos+46,U16(raw,pos+28));Record r;if(!byName.TryGetValue(name,out r)||r.Central!=null)throw new InvalidDataException("Invalid quest archive central entry.");r.Central=Slice(raw,pos,length);pos+=length;centralCount++;}
   if(U32(raw,pos)!=0xf9fab4af||centralCount!=records.Count||pos+22+U16(raw,pos+20)!=raw.Length)throw new InvalidDataException("Invalid native quest archive index.");end=Slice(raw,pos,raw.Length-pos);
   Record first=records.First(r=>r.Size>0);for(int candidate=1;candidate<=2;candidate++){try{byte[] plain=Payload(first,candidate);if(plain.Length==first.Length&&Crc(plain)==first.Crc){version=candidate;break;}}catch(InvalidDataException){}}
   if(version==0)throw new InvalidDataException("Unknown quest archive encoding.");
  }
  public bool Contains(string name){return byName.ContainsKey(name);}public IEnumerable<string> Names{get{return records.Select(r=>r.Name);}}public byte[] Read(string name){Record r=byName[name];byte[] bytes=Payload(r,version);if(bytes.Length!=r.Length||Crc(bytes)!=r.Crc)throw new InvalidDataException("Quest archive CRC mismatch: "+name);return bytes;}
  public QuestPakLocation Locate(string name) {
   Record entry=byName[name];uint offset=0;
   foreach(Record record in records){if(record==entry)break;offset=checked(offset+(uint)record.Local.Length);}
   byte[] key=QuestPakKeys.Keys[version-1];int start=version==1?(entry.Size&31)*32:entry.Size&1023;
   return new QuestPakLocation { Offset=checked(offset+(uint)entry.Header),Compressed=(uint)entry.Size,Size=(uint)entry.Length,Crc=entry.Crc,Method=entry.Method,Key=key.Skip(start).Take(Math.Min(32,entry.Size)).ToArray() };
  }
  static byte[] NativeZip(byte[] bytes,int version){byte[] key=QuestPakKeys.Keys[version-1];int pos=0;while(U32(bytes,pos)==0x04034b50){if((U16(bytes,pos+6)&8)!=0)throw new InvalidDataException("Generated quest archive uses data descriptors.");int size=checked((int)U32(bytes,pos+18)),start=pos+30+U16(bytes,pos+26)+U16(bytes,pos+28),offset=version==1?(size&31)*32:size&1023;for(int i=0;i<Math.Min(32,size);i++)bytes[start+i]^=key[offset+i];Put(bytes,pos,0xfbfcb4af);pos=start+size;}while(U32(bytes,pos)==0x02014b50){Put(bytes,pos,0xfdfeb4af);pos+=46+U16(bytes,pos+28)+U16(bytes,pos+30)+U16(bytes,pos+32);}if(U32(bytes,pos)!=0x06054b50)throw new InvalidDataException("Invalid generated quest archive.");Put(bytes,pos,0xf9fab4af);return bytes;}
  public byte[] Build(Dictionary<string,byte[]> replacements) {
   MemoryStream temp=new MemoryStream();using(ZipArchive zip=new ZipArchive(temp,ZipArchiveMode.Create,true))foreach(var item in replacements){ZipArchiveEntry entry=zip.CreateEntry(item.Key,CompressionLevel.Optimal);using(Stream stream=entry.Open())stream.Write(item.Value,0,item.Value.Length);}
   QuestPak changed=new QuestPak(NativeZip(temp.ToArray(),version));List<Record> ordered=new List<Record>(records);foreach(Record r in changed.records)if(!Contains(r.Name))ordered.Add(r);
   MemoryStream result=new MemoryStream();Dictionary<string,uint> offsets=new Dictionary<string,uint>();foreach(Record r in ordered){offsets.Add(r.Name,(uint)result.Position);Record selected;if(!changed.byName.TryGetValue(r.Name,out selected))selected=r;result.Write(selected.Local,0,selected.Local.Length);}uint centralStart=(uint)result.Position;
   foreach(Record r in ordered){Record selected;if(!changed.byName.TryGetValue(r.Name,out selected))selected=r;byte[] central=(byte[])selected.Central.Clone();Put(central,42,offsets[r.Name]);result.Write(central,0,central.Length);}uint centralLength=(uint)result.Position-centralStart;byte[] tail=(byte[])end.Clone();Array.Copy(BitConverter.GetBytes(checked((ushort)ordered.Count)),0,tail,8,2);Array.Copy(BitConverter.GetBytes(checked((ushort)ordered.Count)),0,tail,10,2);Put(tail,12,centralLength);Put(tail,16,centralStart);result.Write(tail,0,tail.Length);byte[] output=result.ToArray();QuestPak check=new QuestPak(output);
   foreach(var item in replacements)if(!check.Read(item.Key).SequenceEqual(item.Value))throw new InvalidDataException("Generated quest archive payload differs.");foreach(Record r in records)if(!replacements.ContainsKey(r.Name)&&!r.Local.SequenceEqual(check.byName[r.Name].Local))throw new InvalidDataException("Unrelated compressed quest archive member changed.");return output;
  }
 }
}
