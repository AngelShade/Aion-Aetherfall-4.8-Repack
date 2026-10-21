using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.NetworkInformation;
using System.Threading.Tasks;
using System.Web.Script.Serialization;

namespace AetherfallSetup {
 // Explicit local profile: never reinterpret the source installation as a repack.
 public sealed class OriginalInstallation {
  public string ServerRoot, ClientRoot, JavaHome, ReleaseFile, DatabaseExecutable, DatabaseConfig;
  public int DatabasePort=3306, LoginPort=2106, GamePort=7777, ChatPort=10241;
  // Optional: BackupsRoot (quick link, defaults to game-server/backups) and
  // Effects ("aether" light ambience, "classic" motes/pulse, "off").
  public string BackupsRoot, Effects="aether";
  static string Resolve(string root,string value) {
   if(String.IsNullOrWhiteSpace(value))throw new InvalidOperationException("Original Hub profile has an empty path.");
   return Path.GetFullPath(Path.IsPathRooted(value)?value:Path.Combine(root,value));
  }
  public static OriginalInstallation Load(string root) {
   string file=Path.Combine(root,"OriginalHub.json");
   if(!File.Exists(file))return null;
   var profile=new JavaScriptSerializer().Deserialize<OriginalInstallation>(File.ReadAllText(file));
   if(profile==null)throw new InvalidOperationException("Original Hub profile is empty.");
   profile.ServerRoot=Resolve(root,profile.ServerRoot);profile.ClientRoot=Resolve(root,profile.ClientRoot);
   profile.JavaHome=Resolve(root,profile.JavaHome);profile.ReleaseFile=Resolve(root,profile.ReleaseFile);
   profile.DatabaseExecutable=Resolve(root,profile.DatabaseExecutable);profile.DatabaseConfig=Resolve(root,profile.DatabaseConfig);
   profile.BackupsRoot=String.IsNullOrWhiteSpace(profile.BackupsRoot)?Path.Combine(profile.ServerRoot,"game-server","backups"):Resolve(root,profile.BackupsRoot);
   profile.Effects=String.IsNullOrWhiteSpace(profile.Effects)?"aether":profile.Effects.Trim().ToLowerInvariant();
   if(profile.Effects!="aether" && profile.Effects!="classic" && profile.Effects!="off")
    throw new InvalidOperationException("Original Hub profile Effects must be aether, classic or off.");
   if(new[]{profile.DatabasePort,profile.LoginPort,profile.GamePort,profile.ChatPort}.Any(port=>port<1 || port>65535))
    throw new InvalidOperationException("Original Hub profile has an invalid port.");
   foreach(string component in new[]{"game-server","login-server","chat-server"}) {
    if(!File.Exists(Path.Combine(profile.ServerRoot,component,"start.bat")) || !Directory.Exists(Path.Combine(profile.ServerRoot,component,"libs")))
     throw new InvalidOperationException("Original server files are missing: "+component);
   }
   foreach(string path in new[]{Path.Combine(profile.ClientRoot,"Aetherfall Launcher.exe"),Path.Combine(profile.JavaHome,"bin","java.exe"),profile.ReleaseFile,profile.DatabaseExecutable,profile.DatabaseConfig})
    if(!File.Exists(path))throw new InvalidOperationException("Original installation file is missing: "+path);
   return profile;
  }
  public static bool Listening(int port) {
   return IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners().Any(p=>p.Port==port);
  }
  async Task WaitForPort(int port,string component) {
   for(int attempt=0;attempt<120;attempt++) {
    if(Listening(port))return;
    await Task.Delay(250);
   }
   throw new InvalidOperationException(component+" has not opened port "+port+" yet. Check its console before trying again.");
  }
  public async Task StartDatabase() {
   if(Listening(DatabasePort))return;
   // Native console, using the existing original data configuration; no service
   // installation, initialization, credentials or database changes.
   Process.Start(new ProcessStartInfo(DatabaseExecutable,"--defaults-file=\""+DatabaseConfig+"\" --console") {
    WorkingDirectory=Path.GetDirectoryName(DatabaseExecutable),UseShellExecute=true,WindowStyle=ProcessWindowStyle.Normal
   });
   await WaitForPort(DatabasePort,"MariaDB");
  }
  public async Task StartServers() {
   if(!Listening(DatabasePort))throw new InvalidOperationException("Start the original database first.");
   foreach(string component in new[]{"LoginServer","ChatServer","GameServer"}) {
    if(ServerControl.RunningOriginal(JavaHome,component))continue;
    string folder=component=="GameServer"?"game-server":component=="LoginServer"?"login-server":"chat-server";
    string launcher=Path.Combine(ServerRoot,folder,"start.bat");
    var start=new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),"cmd.exe"),"/d /c \"\""+launcher+"\"\"") {
     WorkingDirectory=Path.GetDirectoryName(launcher),UseShellExecute=false,CreateNoWindow=false,WindowStyle=ProcessWindowStyle.Normal
    };
    start.EnvironmentVariables["JAVA_HOME"]=JavaHome;
    start.EnvironmentVariables["PATH"]=Path.Combine(JavaHome,"bin")+";"+Environment.GetEnvironmentVariable("PATH");
    // The Hub already opens an interactive console; avoid start.bat handing
    // that console to another Windows Terminal process and losing ownership.
    start.EnvironmentVariables["WT_SESSION"]="AetherfallHub";
    Process.Start(start);
    if(component=="LoginServer")await WaitForPort(LoginPort,component);
   }
  }
 }
}
