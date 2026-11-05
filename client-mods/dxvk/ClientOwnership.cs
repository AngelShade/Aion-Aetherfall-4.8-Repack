using System;
using System.IO;
using System.IO.Compression;
using System.Text;
using System.Linq;
using System.Collections.Generic;
using System.Xml.Linq;
using System.Runtime.Serialization.Json;
namespace AetherfallOwnership {
 public static class OwnedXml {
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

 public sealed class OwnedPakLocation {
  public uint Offset, Compressed, Size, Crc; public ushort Method; public byte[] Key;
 }
 public sealed class OwnedPak {
  sealed class Record { public string Name;public byte[] Local,Central;public int Header;public ushort Method;public uint Crc;public int Size,Length; }
  readonly List<Record> records=new List<Record>();readonly Dictionary<string,Record> byName=new Dictionary<string,Record>(StringComparer.Ordinal);readonly byte[] end;readonly int version;
  static ushort U16(byte[] b,int p){return BitConverter.ToUInt16(b,p);}static uint U32(byte[] b,int p){return BitConverter.ToUInt32(b,p);}static void Put(byte[] b,int p,uint n){Array.Copy(BitConverter.GetBytes(n),0,b,p,4);}static byte[] Slice(byte[] b,int p,int n){byte[] r=new byte[n];Array.Copy(b,p,r,0,n);return r;}
  static readonly uint[] CrcTable=MakeCrcTable();
  static uint[] MakeCrcTable(){uint[] table=new uint[256];for(uint i=0;i<256;i++){uint value=i;for(int j=0;j<8;j++)value=(value&1)!=0?(value>>1)^0xedb88320:value>>1;table[i]=value;}return table;}
  static uint Crc(byte[] bytes){uint crc=0xffffffff;foreach(byte b in bytes)crc=CrcTable[(crc^b)&255]^(crc>>8);return ~crc;}
  static byte[] Payload(Record r,int version) { byte[] data=Slice(r.Local,r.Header,r.Size);byte[] key=OwnedPakKeys.Keys[version-1];int offset=version==1?(r.Size&31)*32:r.Size&1023;for(int i=0;i<Math.Min(32,data.Length);i++)data[i]^=key[offset+i];if(r.Method==0)return data;if(r.Method!=8)throw new InvalidDataException("Unsupported quest archive compression.");using(DeflateStream deflate=new DeflateStream(new MemoryStream(data),CompressionMode.Decompress))using(MemoryStream plain=new MemoryStream()){deflate.CopyTo(plain);return plain.ToArray();} }
  public OwnedPak(byte[] raw) {
   int pos=0;while(U32(raw,pos)==0xfbfcb4af) { if((U16(raw,pos+6)&8)!=0)throw new InvalidDataException("Quest archive descriptors are unsupported.");int header=30+U16(raw,pos+26)+U16(raw,pos+28),size=checked((int)U32(raw,pos+18));string name=Encoding.UTF8.GetString(raw,pos+30,U16(raw,pos+26));if(byName.ContainsKey(name))throw new InvalidDataException("Duplicate quest archive member.");Record record=new Record { Name=name,Local=Slice(raw,pos,header+size),Header=header,Method=U16(raw,pos+8),Crc=U32(raw,pos+14),Size=size,Length=checked((int)U32(raw,pos+22)) };records.Add(record);byName.Add(name,record);pos+=header+size; }
   int centralCount=0;while(U32(raw,pos)==0xfdfeb4af){int length=46+U16(raw,pos+28)+U16(raw,pos+30)+U16(raw,pos+32);string name=Encoding.UTF8.GetString(raw,pos+46,U16(raw,pos+28));Record r;if(!byName.TryGetValue(name,out r)||r.Central!=null)throw new InvalidDataException("Invalid quest archive central entry.");r.Central=Slice(raw,pos,length);pos+=length;centralCount++;}
   if(U32(raw,pos)!=0xf9fab4af||centralCount!=records.Count||pos+22+U16(raw,pos+20)!=raw.Length)throw new InvalidDataException("Invalid native quest archive index.");end=Slice(raw,pos,raw.Length-pos);
   Record first=records.First(r=>r.Size>0);for(int candidate=1;candidate<=2;candidate++){try{byte[] plain=Payload(first,candidate);if(plain.Length==first.Length&&Crc(plain)==first.Crc){version=candidate;break;}}catch(InvalidDataException){}}
   if(version==0)throw new InvalidDataException("Unknown quest archive encoding.");
  }
  public bool Contains(string name){return byName.ContainsKey(name);}public IEnumerable<string> Names{get{return records.Select(r=>r.Name);}}public byte[] Read(string name){Record r=byName[name];byte[] bytes=Payload(r,version);if(bytes.Length!=r.Length||Crc(bytes)!=r.Crc)throw new InvalidDataException("Quest archive CRC mismatch: "+name);return bytes;}
  public OwnedPakLocation Locate(string name) {
   Record entry=byName[name];uint offset=0;
   foreach(Record record in records){if(record==entry)break;offset=checked(offset+(uint)record.Local.Length);}
   byte[] key=OwnedPakKeys.Keys[version-1];int start=version==1?(entry.Size&31)*32:entry.Size&1023;
   return new OwnedPakLocation { Offset=checked(offset+(uint)entry.Header),Compressed=(uint)entry.Size,Size=(uint)entry.Length,Crc=entry.Crc,Method=entry.Method,Key=key.Skip(start).Take(Math.Min(32,entry.Size)).ToArray() };
  }
  static byte[] NativeZip(byte[] bytes,int version){byte[] key=OwnedPakKeys.Keys[version-1];int pos=0;while(U32(bytes,pos)==0x04034b50){if((U16(bytes,pos+6)&8)!=0)throw new InvalidDataException("Generated quest archive uses data descriptors.");int size=checked((int)U32(bytes,pos+18)),start=pos+30+U16(bytes,pos+26)+U16(bytes,pos+28),offset=version==1?(size&31)*32:size&1023;for(int i=0;i<Math.Min(32,size);i++)bytes[start+i]^=key[offset+i];Put(bytes,pos,0xfbfcb4af);pos=start+size;}while(U32(bytes,pos)==0x02014b50){Put(bytes,pos,0xfdfeb4af);pos+=46+U16(bytes,pos+28)+U16(bytes,pos+30)+U16(bytes,pos+32);}if(U32(bytes,pos)!=0x06054b50)throw new InvalidDataException("Invalid generated quest archive.");Put(bytes,pos,0xf9fab4af);return bytes;}
  public byte[] Build(Dictionary<string,byte[]> replacements) {
   MemoryStream temp=new MemoryStream();using(ZipArchive zip=new ZipArchive(temp,ZipArchiveMode.Create,true))foreach(var item in replacements){ZipArchiveEntry entry=zip.CreateEntry(item.Key,CompressionLevel.Optimal);using(Stream stream=entry.Open())stream.Write(item.Value,0,item.Value.Length);}
   OwnedPak changed=new OwnedPak(NativeZip(temp.ToArray(),version));List<Record> ordered=new List<Record>(records);foreach(Record r in changed.records)if(!Contains(r.Name))ordered.Add(r);
   MemoryStream result=new MemoryStream();Dictionary<string,uint> offsets=new Dictionary<string,uint>();foreach(Record r in ordered){offsets.Add(r.Name,(uint)result.Position);Record selected;if(!changed.byName.TryGetValue(r.Name,out selected))selected=r;result.Write(selected.Local,0,selected.Local.Length);}uint centralStart=(uint)result.Position;
   foreach(Record r in ordered){Record selected;if(!changed.byName.TryGetValue(r.Name,out selected))selected=r;byte[] central=(byte[])selected.Central.Clone();Put(central,42,offsets[r.Name]);result.Write(central,0,central.Length);}uint centralLength=(uint)result.Position-centralStart;byte[] tail=(byte[])end.Clone();Array.Copy(BitConverter.GetBytes(checked((ushort)ordered.Count)),0,tail,8,2);Array.Copy(BitConverter.GetBytes(checked((ushort)ordered.Count)),0,tail,10,2);Put(tail,12,centralLength);Put(tail,16,centralStart);result.Write(tail,0,tail.Length);byte[] output=result.ToArray();OwnedPak check=new OwnedPak(output);
   foreach(var item in replacements)if(!check.Read(item.Key).SequenceEqual(item.Value))throw new InvalidDataException("Generated quest archive payload differs.");foreach(Record r in records)if(!replacements.ContainsKey(r.Name)&&!r.Local.SequenceEqual(check.byName[r.Name].Local))throw new InvalidDataException("Unrelated compressed quest archive member changed.");return output;
  }
 }
}

// Native PAK tables shared with client-mods/expanded-warehouse/codec.py.
namespace AetherfallOwnership { internal static class OwnedPakKeys { internal static readonly byte[][] Keys = {
System.Convert.FromBase64String("L11R9wHptJNOUYE+rz/fmYBeE4ObRle1G1zssSl8qTFo5dqn9k+uFpp/A88dXtBRWuUC2RHQ+/T4fKKIJtgfokPaM6msTloN7XiGLbJqxJuqd4VXaqbYNdiXaxckt3od0zueefKunwHmnSlA7S+cFtoY0ZkO1ApjLZLX67SnUCHYD0XWxr/MR8xZ7T5x/qAm/NEHhYruEjYRWmDhj72e97ZkOc1JWpr3kBzBoguzgffKuCpLlRPcLkrlZBaUmcmxe1N2rsTfJvfIX3gxrq9af6TnKV4O4ruRQTIs8M5gnifc+twTrDf38bSkzfR63Kl7lYLaffuNa24MQ+cjbMBT+TmCON6b0P5XPXVlQ7CuWm5Os/uujMQPm2Unr6LG8YSRlBo5OVOlkGTwYsy1vx68pyiuMz8WxjC3sfKDsV6wNyCd93uVvjVuGwcFdzI6roo5Ja8QxRhWwiv5xEvW3ETXnahcf63viLxGX/7A495p4wPt+AYfOMEiI/TB1+ERezzLtI2vgiMwDXiC+e0+keFSp9XVdXFG2hGX+xbf6vOroDJm215euUNVDp6lKv1eMcaT1JqiKzcAuUYT9wVRp7KqIgydxdI9YvQojLyJJXn6mv2NobwCKxWwtuakzbxy+Gi0mjMIumK3sbHKAAgBQGiO4UlP2PJnhfA3yWGrHsZqTcqvAy82AvC8XoE5iiU4LMoE+Q32RFtG2963e/SsO382DZB8LLAgSKupfznbbQuA4vE3UPqDndM+jFRI6+eSNGrrKxja2uV8ftM92bH9kCjNAEWTs4brMkvm6iS2l7QRlKAWU/uuptea6dn7pkHCbexLC1nXbC7sm11vdmbLsCPKLI22Om7cKdG9HYk/68ciCbgdLgSYcRo1Jn2q8tvAAYpWdtEnoyvIWOp2cub56qBU9LKkwLvsVIE/WDc8aUXIt7FgOz0gW5fO0vyx8q+iy2d0rVh5yP7BVHHqmAtZxiGglH+R3v1h/DyhcUefl4kNQ3SX7IX+Lg3nScpVDt30OPgisX5VnlbqD0o6PQ+GZFdR+aMMI+Qqat8gMfjdbajE30J9rtKsfdcfhWekT5chJWHQqWt3R8eXRxMDGvrI4gXXpg7acRhCxarYsJZTL9N4rY8rxJE7B9eQCctVzPfMvc/FO8E0HTU8WY11Nff3t9vWkFPbZiAO95iwvVGkSbQ/HeKCKwQ8E0s5tr2oAOczYOX68XvVmytMn4G2ubhVFl96BQfmsz68i8MvNyMZOdGiTLqBeKOZ07BTuThEK/yPew/+mcr7Nz4d1Jk83dVvSMLhgyOrf1KpicRhb64CZul6Z2ett4B/yKi1Yckas1dzbOnToPr+Q3DDcUYuvi4CF8p4oA=="),
System.Convert.FromBase64String("hvoaHAe92GTO7lmIzakdBvc9MViDoVx+36ZQnomoEtIlSXXiBw/rAZdKZjWrMp2nTqKJYg9VQcVSEB9HsKBjpvAcHEybPKzis06f8aSRKYLkdg2NT6M0SswcxxhIjv4YeQiHKI4kt2s48lgBLahYDpxUKc+hrgrSO0oQ+NgZMX3zrhuQ0i8Wx+U7zO/h4SyGAN01Z40l/O0yH6kaEm2w9z22H+iBTTbnJTAhkIYwDu5Avm7awTqv8uwoLPHNRJhy2s3G2d/37ogE4WIACA7NFjer+fUUqi4ATvgYQQvZb5v6rStUVi5/LDtqgqF8fKaPZl7nz4O56vziMdQQ8/Qi7HMUT5R4eY8eKepfIR4IN7j2mi3FNjTBl9x1sq3X4wSnwMkcGgDpLW/WjbxzUsCKtrospn17b/RHGnLpsjB91NMJnGWw0BfP/PL/RtKmQxF2K+Ud5clHL0sb3Zr9nSC2Qxpk42jzIVdo1ASPw86vo6tpozw0vh+EqA50y7fmsTmNaAA6m5yxCRx9UhUSprCD00BHm+Qi424wxPxvT/6fURQTV/HrJfeVTJK2PNA0eVkzIL64v+AK0ne8Q1x9/OFZAN5afUQRrBPyZIRPXaLENtcj+vjRFI353RcdUkEi9RpCOf421QoQAdLqEoJaSNKUlQr3q3D38piJoWj54dbhvZI4RV8Z4upGdsXD8rSfcFMJP7gGOvNGyGrNCuPwqjTZcpg0I9GWjDIyOwCjnk/tvJfUSiYVlh0ONrjuhkVXBG0rwNuRCkbOfB88OoGUIiaCbYO9Ey2WkVNsJgxE/r3u2sy9UqYRPhBCIGDrX1sNfLuArC+5+dJK61SAYGKF5RrwMEW3RILvOgzg5ZT6/S7Z641awu85UXGS+tvvFIgA/+P2tTQ0QPW7yNO1vfbPx7H5GD2idO9AvGs58shuAGR4UogT9Cd0FI/ONF754G1H/DhtsAPtbPZoAKwr/nMslJ4/FwwzuY8zNN4FGOEruUI/X6K0HulF8zhDu46wCpQ57v+a9C1sS2axHg/CGDLhdP+QlPI43VbceJGW0QQDCSE5stTMKqir6Jkc5+RDO1jBWVTovZwoxoH8rTNPJBagR9FMTTl6wfcdBM/nrhdx2TfcnAoOnQ4E1yTBUAxJ47zKmIlVhnPxw42PmTT3S+dpCrDBL4WXv8P90GJ1sa3zBPPzdwaqd1rn62c/tUChnFOW/YVTbu1SBTtuie+VmLZmNNCKP0TqBoYTOe8greRzLGF3ED25C8IM/fKZ2LFXgxskpqCrlz7lCQc/Q+0S4zbOFljyeABj92fc2V8Nqj6ao4Ny/rqS6dQi8Dg4YeJ5m16KYidZhHHA65UoDTTLqyXGO7xSpcprk8ojbTWHQYc+SLnfDv0wuNG4EGg9vAkEMZRcka9s")
}; } }

namespace AetherfallOwnership {
 public sealed class BytePart { public int offset; public string installed,original; }
 public sealed class UiPart { public string path,entry; public string[] widgets; }
 public sealed class GraphicsParts { public int schema=2,sectionOffset; public string sectionHeader,dllHash,descriptor; public BytePart[] ranges; public UiPart[] ui; }
 public static class Parts {
  static GraphicsParts FromJson(string text){using(var stream=new MemoryStream(Encoding.UTF8.GetBytes(text)))return (GraphicsParts)new DataContractJsonSerializer(typeof(GraphicsParts)).ReadObject(stream);}
  static string ToJson(GraphicsParts value){using(var stream=new MemoryStream()){new DataContractJsonSerializer(typeof(GraphicsParts)).WriteObject(stream,value);return Encoding.UTF8.GetString(stream.ToArray());}}
  static byte[] Hex(string s){return Enumerable.Range(0,s.Length/2).Select(i=>Convert.ToByte(s.Substring(i*2,2),16)).ToArray();}
  static string Hex(byte[] b){return BitConverter.ToString(b).Replace("-","").ToLowerInvariant();}
  static byte[] Slice(byte[] b,int p,int n){byte[] v=new byte[n];Array.Copy(b,p,v,0,n);return v;}
  static void Put(byte[] b,int p,uint v){Array.Copy(BitConverter.GetBytes(v),0,b,p,4);}
  static void Set(byte[] b,int p,byte[] v){Array.Copy(v,0,b,p,v.Length);}
  static bool At(byte[] b,int p,byte[] v){return p>=0 && p<=b.Length-v.Length && Slice(b,p,v.Length).SequenceEqual(v);}
  static void Require(bool condition,string message){if(!condition)throw new InvalidDataException(message);}
  public static void Atomic(string path,byte[] bytes){
   string temp=path+".ownership-"+Guid.NewGuid().ToString("N")+".tmp";
   try { using(FileStream f=new FileStream(temp,FileMode.CreateNew,FileAccess.Write,FileShare.None)){f.Write(bytes,0,bytes.Length);f.Flush(true);}if(File.Exists(path))File.Replace(temp,path,null);else File.Move(temp,path); }
   finally {if(File.Exists(temp))File.Delete(temp);}
  }
  static string Hash(byte[] b){using(var s=System.Security.Cryptography.SHA256.Create())return Hex(s.ComputeHash(b));}
  sealed class Pe {
   internal byte[] b;internal int pe,opt,table,count;
   internal Pe(byte[] raw){b=raw;pe=BitConverter.ToInt32(b,60);Require(At(b,pe,new byte[]{80,69,0,0,100,134}),"Graphics menu requires the supported x64 Game.dll.");opt=pe+24;table=opt+BitConverter.ToUInt16(b,pe+20);count=BitConverter.ToUInt16(b,pe+6);}
   internal int Offset(uint rva){for(int i=0;i<count;i++){int h=table+i*40;uint va=BitConverter.ToUInt32(b,h+12),size=BitConverter.ToUInt32(b,h+16);if(rva>=va && rva-va<size)return checked((int)(BitConverter.ToUInt32(b,h+20)+rva-va));}throw new InvalidDataException("Graphics menu: unmapped native address.");}
   internal int Imports{get{return Offset(BitConverter.ToUInt32(b,opt+120));}}
   internal List<byte[]> Descriptors(){var result=new List<byte[]>();int p=Imports;for(int i=0;i<4096;i++,p+=20){var d=Slice(b,p,20);if(d.All(x=>x==0))return result;result.Add(d);}throw new InvalidDataException("Graphics menu import table has no terminator.");}
   internal string Module(byte[] d){int p=Offset(BitConverter.ToUInt32(d,12)),end=Array.IndexOf(b,(byte)0,p);Require(end>=p && end-p<256,"Graphics menu import name is damaged.");return Encoding.ASCII.GetString(b,p,end-p);}
  }
  static string Canon(XElement n){return new XElement(n.Name,n.Attributes().OrderBy(a=>a.Name.ToString()).Select(a=>new XAttribute(a)),n.Nodes().Select(x=>x is XElement ? (object)XElement.Parse(Canon((XElement)x)) : x)).ToString(SaveOptions.DisableFormatting);}
  static XElement Page(XElement root){var pages=root.DescendantsAndSelf("Widget").Where(x=>(string)x.Attribute("name")=="page_screen").ToList();Require(pages.Count==1,"Graphics menu: Screen page is missing or duplicated.");return pages[0];}
  static void VerifyWidgets(XElement root,UiPart part){
   var page=Page(root);
   foreach(string text in part.widgets){var expected=XElement.Parse(text);string name=(string)expected.Attribute("name");var found=root.DescendantsAndSelf("Widget").Where(x=>(string)x.Attribute("name")==name).ToList();Require(found.Count==1 && found[0].Parent==page && Canon(found[0])==Canon(expected),"Graphics menu control damaged: "+name+" in "+part.path+". Repair the Graphics menu before launching.");}
  }
  public static void VerifyGraphics(string root,string specJson){
   GraphicsParts s=FromJson(specJson);Require(s.schema==2,"Unsupported Graphics menu ownership definition.");
   string dll=Path.Combine(root,"bin64/AionGraphicsMenu.dll");Require(File.Exists(dll)&&Hash(File.ReadAllBytes(dll))==s.dllHash.ToLowerInvariant(),"Graphics menu DLL damaged or missing: bin64/AionGraphicsMenu.dll. Repair it before launching.");
   byte[] b=File.ReadAllBytes(Path.Combine(root,"bin64/Game.dll"));Pe p=new Pe(b);
   Require(At(b,s.sectionOffset,Hex(s.sectionHeader)),"Graphics menu import section header damaged in Game.dll.");
   foreach(var r in s.ranges)Require(At(b,r.offset,Hex(r.installed)),"Graphics menu hook/import bytes damaged in Game.dll at 0x"+r.offset.ToString("x")+". Repair it before launching.");
   var own=p.Descriptors().Where(d=>p.Module(d).Equals("AionGraphicsMenu.dll",StringComparison.OrdinalIgnoreCase)).ToList();Require(own.Count==1 && own[0].SequenceEqual(Hex(s.descriptor)),"Graphics menu active import damaged or missing in Game.dll. Repair it before launching.");
   foreach(var u in s.ui){var pak=new OwnedPak(File.ReadAllBytes(Path.Combine(root,u.path)));VerifyWidgets(OwnedXml.Decode(pak.Read(u.entry)),u);}
  }
  static readonly string[] CursorPaths={"bin64/XRenderD3D9.dll","bin64/Game.dll"};
  static readonly int[] CursorOffsets={0x1202d7,0x551c96};
  static readonly byte[][] CursorOriginal={new byte[]{0x7c,0x0c},new byte[]{0x78,0x18}};
  public static void VerifyCursor(string root){for(int i=0;i<2;i++){byte[] b=File.ReadAllBytes(Path.Combine(root,CursorPaths[i]));new Pe(b);Require(At(b,CursorOffsets[i],new byte[]{0x90,0x90}),"Native cursor instructions damaged in "+CursorPaths[i]+" at 0x"+CursorOffsets[i].ToString("x")+". Repair CursorFix before launching.");}}
  public static void VerifyCursorOriginal(string root){for(int i=0;i<2;i++)Require(At(File.ReadAllBytes(Path.Combine(root,CursorPaths[i])),CursorOffsets[i],CursorOriginal[i]),"Restored cursor instructions changed in "+CursorPaths[i]+".");}
  static void Transaction(string root,Dictionary<string,byte[]> writes){
   var before=writes.Keys.ToDictionary(k=>k,k=>File.Exists(Path.Combine(root,k))?File.ReadAllBytes(Path.Combine(root,k)):null);var done=new List<string>();
   try{foreach(var e in writes){string p=Path.Combine(root,e.Key);if(e.Value==null)File.Delete(p);else Atomic(p,e.Value);done.Add(e.Key);}}
   catch {foreach(string k in done.AsEnumerable().Reverse()){string p=Path.Combine(root,k);if(before[k]==null)File.Delete(p);else Atomic(p,before[k]);}throw;}
  }
  public static void Cursor(string root,bool restore){
   var writes=new Dictionary<string,byte[]>();
   for(int i=0;i<2;i++){byte[] b=File.ReadAllBytes(Path.Combine(root,CursorPaths[i]));new Pe(b);byte[] want=restore?CursorOriginal[i]:new byte[]{0x90,0x90},prior=restore?new byte[]{0x90,0x90}:CursorOriginal[i];Require(At(b,CursorOffsets[i],prior)||At(b,CursorOffsets[i],want),"Unsupported cursor instructions in "+CursorPaths[i]+"; no files changed.");Set(b,CursorOffsets[i],want);writes.Add(CursorPaths[i],b);}
   Transaction(root,writes);
  }
  public static void RestoreGraphics(string root,string specJson){
   VerifyGraphics(root,specJson);GraphicsParts s=FromJson(specJson);var writes=new Dictionary<string,byte[]>();byte[] b=File.ReadAllBytes(Path.Combine(root,"bin64/Game.dll"));Pe p=new Pe(b);
   foreach(var r in s.ranges)if(r.original!=null)Set(b,r.offset,Hex(r.original));
   // The active import table can belong to a later mod. Remove just our descriptor.
   var descriptors=p.Descriptors();int index=descriptors.FindIndex(d=>p.Module(d).Equals("AionGraphicsMenu.dll",StringComparison.OrdinalIgnoreCase));descriptors.RemoveAt(index);int start=p.Imports;
   for(int i=0;i<descriptors.Count;i++)Set(b,start+i*20,descriptors[i]);Set(b,start+descriptors.Count*20,new byte[40]);Put(b,p.opt+124,(uint)((descriptors.Count+1)*20));
   // Retain PE section allocation: later mods can follow it. Clear only our payload.
   foreach(var r in s.ranges)if(r.original==null)Set(b,r.offset,new byte[Hex(r.installed).Length]);
   writes.Add("bin64/Game.dll",b);
   foreach(var u in s.ui){var pak=new OwnedPak(File.ReadAllBytes(Path.Combine(root,u.path)));var xml=OwnedXml.Decode(pak.Read(u.entry));foreach(string text in u.widgets){string name=(string)XElement.Parse(text).Attribute("name");Page(xml).Elements("Widget").Single(x=>(string)x.Attribute("name")==name).Remove();}writes.Add(u.path,pak.Build(new Dictionary<string,byte[]>{{u.entry,OwnedXml.Encode(xml)}}));}
   writes.Add("bin64/AionGraphicsMenu.dll",null);Transaction(root,writes);
  }
  static int Align(int value,int by){return checked((value+by-1)/by*by);}
  static int Append(List<byte> data,byte[] value,int boundary,uint rva){while(data.Count%boundary!=0)data.Add(0);int address=checked((int)rva+data.Count);data.AddRange(value);return address;}
  public static string InstallGraphics(string root,string templateJson,byte[] dll,bool apply){
   GraphicsParts s=FromJson(templateJson);Require(Hash(dll)==s.dllHash,"Graphics menu DLL payload changed.");
   byte[] before=File.ReadAllBytes(Path.Combine(root,"bin64/Game.dll"));Pe p=new Pe(before);var imports=p.Descriptors();Require(!imports.Any(d=>p.Module(d).Equals("AionGraphicsMenu.dll",StringComparison.OrdinalIgnoreCase)),"Graphics menu already imported without an active record.");
   int header=p.table+p.count*40;bool reuse=false;
   if(At(before,s.sectionOffset,Hex(s.sectionHeader)) && s.ranges.Where(x=>x.original==null).All(x=>At(before,x.offset,new byte[Hex(x.installed).Length]))){header=s.sectionOffset;reuse=true;}
   else Require(header+40<=BitConverter.ToUInt32(before,p.opt+60)&&At(before,header,new byte[40]),"No spare native PE section header; Graphics menu installation stopped.");
   int sa=BitConverter.ToInt32(before,p.opt+32),fa=BitConverter.ToInt32(before,p.opt+36),end=0;
   for(int i=0;i<p.count;i++){int h=p.table+i*40;end=Math.Max(end,checked(BitConverter.ToInt32(before,h+12)+Math.Max(BitConverter.ToInt32(before,h+8),BitConverter.ToInt32(before,h+16))));}
   uint rva=reuse?BitConverter.ToUInt32(before,header+12):(uint)Align(end,sa);int raw=reuse?BitConverter.ToInt32(before,header+20):Align(before.Length,fa);var payload=new List<byte>();foreach(var d in imports)payload.AddRange(d);int descriptor=payload.Count;payload.AddRange(new byte[40]);
   int module=Append(payload,Encoding.ASCII.GetBytes("AionGraphicsMenu.dll\0"),1,rva);int[] names={Append(payload,new byte[2].Concat(Encoding.ASCII.GetBytes("AionGraphicsClick\0")).ToArray(),2,rva),Append(payload,new byte[2].Concat(Encoding.ASCII.GetBytes("AionGraphicsLoad\0")).ToArray(),2,rva)};
   byte[] thunks=BitConverter.GetBytes((long)names[0]).Concat(BitConverter.GetBytes((long)names[1])).Concat(new byte[8]).ToArray();int lookup=Append(payload,thunks,8,rva),iat=Append(payload,thunks,8,rva);
   byte[] desc=new byte[20];Put(desc,0,(uint)lookup);Put(desc,12,(uint)module);Put(desc,16,(uint)iat);for(int i=0;i<20;i++)payload[descriptor+i]=desc[i];
   int[] sites={0x6f8650,0x6fca60};string[] originals={"488bc44881ec88000000","488bc44883ec68"};var ranges=new List<BytePart>();var hooks=new Dictionary<int,byte[]>();
   for(int i=0;i<2;i++){
    byte[] original=Hex(originals[i]);int pos=p.Offset((uint)sites[i]);Require(At(before,pos,original),"Unsupported Graphics menu hook prologue at 0x"+sites[i].ToString("x")+"; no files changed.");
    int hook=Append(payload,new byte[0],16,rva);var code=new List<byte>(Hex("4883ec4848894c242048895424284c894424304c894c2438"));int at=hook+code.Count;code.AddRange(new byte[]{0xff,0x15});code.AddRange(BitConverter.GetBytes(iat+i*8-at-6));code.AddRange(Hex("488b4c2420488b5424284c8b4424304c8b4c24384883c448"));if(i==0)code.AddRange(Hex("85c07401c3"));code.AddRange(original);code.Add(0xe9);code.AddRange(BitConverter.GetBytes(sites[i]+original.Length-(hook+code.Count+4)));payload.AddRange(code);
    byte[] jump=new byte[]{0xe9}.Concat(BitConverter.GetBytes(hook-sites[i]-5)).Concat(Enumerable.Repeat((byte)0x90,original.Length-5)).ToArray();hooks.Add(pos,jump);ranges.Add(new BytePart{offset=pos,installed=Hex(jump),original=originals[i]});
   }
   int vs=payload.Count,rs=reuse?BitConverter.ToInt32(before,header+16):Align(vs,fa);Require(vs<=rs,"Reserved Graphics section is too small for current imports; no files changed.");payload.AddRange(new byte[rs-vs]);byte[] b=new byte[Math.Max(before.Length,raw+rs)];Set(b,0,before);Set(b,raw,payload.ToArray());foreach(var h in hooks)Set(b,h.Key,h.Value);
   byte[] sh=new byte[40];Set(sh,0,Encoding.ASCII.GetBytes(".agfx"));Put(sh,8,(uint)vs);Put(sh,12,rva);Put(sh,16,(uint)rs);Put(sh,20,(uint)raw);Put(sh,36,0xe0000020);Set(b,header,sh);if(!reuse){Array.Copy(BitConverter.GetBytes((ushort)(p.count+1)),0,b,p.pe+6,2);Put(b,p.opt+56,(uint)Align((int)rva+vs,sa));Put(b,p.opt+4,BitConverter.ToUInt32(b,p.opt+4)+(uint)rs);}Put(b,p.opt+64,0);Put(b,p.opt+120,rva);Put(b,p.opt+124,(uint)(imports.Count*20+40));
   // Other modules' copied descriptors are outside our owned range.
   ranges.Add(new BytePart{offset=raw+descriptor,installed=Hex(Slice(b,raw+descriptor,vs-descriptor))});s.ranges=ranges.ToArray();s.sectionOffset=header;s.sectionHeader=Hex(sh);s.descriptor=Hex(desc);
   var writes=new Dictionary<string,byte[]>{{"bin64/Game.dll",b},{"bin64/AionGraphicsMenu.dll",dll}};
   foreach(var u in s.ui){var pak=new OwnedPak(File.ReadAllBytes(Path.Combine(root,u.path)));var xml=OwnedXml.Decode(pak.Read(u.entry));var page=Page(xml);foreach(string text in u.widgets){var widget=XElement.Parse(text);Require(!xml.DescendantsAndSelf("Widget").Any(x=>(string)x.Attribute("name")== (string)widget.Attribute("name")),"Graphics menu widget already exists outside its record.");page.Add(widget);}writes.Add(u.path,pak.Build(new Dictionary<string,byte[]>{{u.entry,OwnedXml.Encode(xml)}}));}
   if(apply)Transaction(root,writes);return ToJson(s);
  }
 }
}
