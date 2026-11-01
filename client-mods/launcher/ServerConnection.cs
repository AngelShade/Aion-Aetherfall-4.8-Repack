using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;

namespace Aetherfall {
 public static class ServerConnection {
  public static string ValidateHost(string value) {
   value=(value??"").Trim();
   if(value.Length==0 || value.Length>253 || Uri.CheckHostName(value.Trim('[',']'))==UriHostNameType.Unknown)
    throw new InvalidDataException("Enter a server IP address or hostname, without a port or URL.");
   return value;
  }
  public static void Save(string path,string host,int port) {
   ValidateHost(host);
   if(port<1 || port>65535)throw new InvalidDataException("Login port must be between 1 and 65535.");
   var json=new JavaScriptSerializer();var values=json.Deserialize<Dictionary<string,object>>(File.ReadAllText(path));
   string args=Convert.ToString(values["Arguments"]);
   args=Regex.Replace(args,@"(?i)(?:^|\s)-ip:[^\s]+|(?:^|\s)-port:[^\s]+","").Trim();
   values["Arguments"]=args+" -ip:"+host+" -port:"+port;
   string temporary=path+"."+Guid.NewGuid().ToString("N")+".tmp";
   try {File.WriteAllText(temporary,json.Serialize(values),new UTF8Encoding(false));File.Replace(temporary,path,null);}
   finally {if(File.Exists(temporary))File.Delete(temporary);}
  }
 }
}
