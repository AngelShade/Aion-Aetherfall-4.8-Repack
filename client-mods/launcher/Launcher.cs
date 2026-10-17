using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Net.NetworkInformation;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using System.Windows.Threading;

namespace Aetherfall {
 public sealed class ReleaseInfo { public string Product, Version; }
 public sealed class ModInfo { public string Title, Description; public bool Listed=true; public string[] Files=new string[0]; }
 public sealed class InstalledFile { public string path, installedSha256; }
 public sealed class ModInventory { public Dictionary<string,List<InstalledFile>> features=new Dictionary<string,List<InstalledFile>>(); }
 public sealed class Preferences { public bool Animations = true; public bool CloseAfter = false; }
 public sealed class ConnectionSettings { public string Arguments; }
 public sealed class Step { public string Title, Script; public string[] Arguments; public Step(string title, string script, params string[] args) { Title=title; Script=script; Arguments=args; } }
 public sealed class Launcher {
  Window window; Grid scene; string root, support, logPath, gameArgs, host; int port=2106;
  bool busy, closeRequested, closed, polling, preview; Mutex mutex;
  Preferences preferences=new Preferences(); DispatcherTimer timer;
  readonly List<Ellipse> particles=new List<Ellipse>(); readonly Random random=new Random(48);
  int ticks;
  [DllImport("user32.dll")] static extern bool AllowSetForegroundWindow(uint processId);
  [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr window);
  [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
  T Find<T>(string name) where T:class { return window.FindName(name) as T; }
  TextBlock Text(string name) { return Find<TextBlock>(name); }
  Button Button(string name) { return Find<Button>(name); }
  static string Quote(string value) { return "\""+value.Replace("\"","\\\"")+"\""; }
  static SolidColorBrush Brush(string color) { return (SolidColorBrush)new BrushConverter().ConvertFromString(color); }

  [STAThread] public static int Main(string[] args) {
   try {
    Application app=new Application(); app.ShutdownMode=ShutdownMode.OnMainWindowClose;
    Launcher launcher=new Launcher(); launcher.Initialize(args);
    if (launcher.preview) { launcher.RenderPreview(args); return 0; }
    app.Run(launcher.window); return 0;
   } catch(Exception error) {
    int at=Array.IndexOf(args,"--preview");
    if(at>=0 && at+1<args.Length) File.WriteAllText(args[at+1]+".error.txt",error.ToString());
    else MessageBox.Show("The Aetherfall launcher could not open.\n\n"+error.Message,"Aion Aetherfall",MessageBoxButton.OK,MessageBoxImage.Error);
    return 1;
   }
  }
  void Initialize(string[] args) {
   root=AppDomain.CurrentDomain.BaseDirectory.TrimEnd(System.IO.Path.DirectorySeparatorChar);
   for(int i=0;i+1<args.Length;i++) if(args[i]=="--client") root=System.IO.Path.GetFullPath(args[i+1]);
   preview=args.Contains("--preview"); support=System.IO.Path.Combine(root,"Mods","Launcher");
   if(!Directory.Exists(support)) throw new DirectoryNotFoundException("Launcher files are missing from Mods\\Launcher.");
   string key; using(SHA256 hash=SHA256.Create()) key=BitConverter.ToString(hash.ComputeHash(Encoding.UTF8.GetBytes(root.ToUpperInvariant()))).Replace("-","").Substring(0,20);
   bool created; mutex=new Mutex(true,"Local\\AetherfallLauncher-"+key,out created);
   if(!created && !preview) throw new InvalidOperationException("The launcher is already open for this client.");
   using(Stream stream=Assembly.GetExecutingAssembly().GetManifestResourceStream("Aetherfall.MainWindow.xaml")) window=(Window)XamlReader.Load(stream);
   scene=Find<Grid>("Scene");
   string iconPath=System.IO.Path.Combine(root,"AionClient.ico"); if(File.Exists(iconPath)) window.Icon=BitmapFrame.Create(new Uri(iconPath));
   ImageSource hero=LoadImage("atreia.png"), frame=LoadImage("frame.png");
   Find<Image>("Hero").Source=hero; Find<Image>("Frame").Source=frame;
   Find<Image>("AionLogo").Source=new CroppedBitmap(LoadImage("aion-native-atlas.png"),new Int32Rect(0,272,624,162));
   Find<Image>("AetherfallLogo").Source=new CroppedBitmap(LoadImage("aetherfall-logo.png"),new Int32Rect(0,146,2048,460));
   double availableW=SystemParameters.WorkArea.Width-35, availableH=SystemParameters.WorkArea.Height-35;
   double scale=Math.Min(1,Math.Min(availableW/1180,availableH/744)); window.Width=1180*scale; window.Height=744*scale;
   string preferencePath=System.IO.Path.Combine(support,"preferences.json");
   try { if(File.Exists(preferencePath)) preferences=new JavaScriptSerializer().Deserialize<Preferences>(File.ReadAllText(preferencePath))??new Preferences(); } catch { preferences=new Preferences(); }
   Find<CheckBox>("Animations").IsChecked=preferences.Animations; Find<CheckBox>("CloseAfter").IsChecked=preferences.CloseAfter;
   bool vulkan=ReadVulkan(); Find<RadioButton>("Vulkan").IsChecked=vulkan; Find<RadioButton>("Direct3D").IsChecked=!vulkan;
   ReadGameArguments(); Text("ServerAddress").Text=host+" : "+port;
   Find<Border>("DragArea").MouseLeftButtonDown+=delegate { if(Mouse.LeftButton==MouseButtonState.Pressed) window.DragMove(); };
   Button("Minimize").Click+=delegate { window.WindowState=WindowState.Minimized; };
   Button("Exit").Click+=delegate { window.Close(); };
   Button("OverviewTab").Click+=delegate { Page("Overview"); };
   Button("ModsTab").Click+=delegate { AddMods(); Page("Mods"); };
   Button("SettingsTab").Click+=delegate { Page("Settings"); };
   Button("Folder").Click+=delegate { Open(root); };
   Button("OpenMods").Click+=delegate { Open(System.IO.Path.Combine(root,"Mods")); };
   Button("OpenLog").Click+=delegate { if(logPath!=null) Open(logPath); };
   Button("SaveSettings").Click+=delegate { SaveSettings(); };
   Button("Play").Click+=async delegate { await Launch(); };
   window.Closing+=delegate(object sender,System.ComponentModel.CancelEventArgs e) {
    if(busy) { e.Cancel=true; closeRequested=true; }
   };
   window.Closed+=delegate { closed=true; if(timer!=null) timer.Stop(); try { mutex.ReleaseMutex(); } catch(ApplicationException) {} mutex.Dispose(); };
   ReadRelease(); AddMods(); AddParticles(); SetMotion();
   timer=new DispatcherTimer { Interval=TimeSpan.FromMilliseconds(50) }; timer.Tick+=async delegate { Animate(); if(++ticks%100==1) await PollServer(); };
   if(!preview) timer.Start();
   window.Loaded+=async delegate { if(!preview) await PollServer(); };
  }
  BitmapImage LoadImage(string name) {
   BitmapImage image=new BitmapImage(); image.BeginInit(); image.CacheOption=BitmapCacheOption.OnLoad;
   image.UriSource=new Uri(System.IO.Path.Combine(support,"Assets",name)); image.EndInit(); image.Freeze(); return image;
  }
  void ReadGameArguments() {
   string path=System.IO.Path.Combine(support,"connection.json");
   if(!File.Exists(path)) throw new FileNotFoundException("Connection settings are missing from Mods\\Launcher\\connection.json.");
   ConnectionSettings connection=new JavaScriptSerializer().Deserialize<ConnectionSettings>(File.ReadAllText(path));
   gameArgs=connection==null?null:connection.Arguments;
   if(String.IsNullOrWhiteSpace(gameArgs) || gameArgs.IndexOfAny(new char[]{'&','|','<','>','\r','\n','\0'})>=0) throw new InvalidDataException("Invalid client arguments in Mods\\Launcher\\connection.json.");
   gameArgs=gameArgs.Trim(); port=2106;
   Match ip=Regex.Match(gameArgs,@"(?:^|\s)-ip:([^\s]+)"); host=ip.Success?ip.Groups[1].Value:"127.0.0.1";
   Match portMatch=Regex.Match(gameArgs,@"(?:^|\s)-port:(\d+)");
   if(portMatch.Success && (!int.TryParse(portMatch.Groups[1].Value,out port)||port<1||port>65535)) throw new InvalidDataException("Invalid login port in Mods\\Launcher\\connection.json.");
  }
  bool ReadVulkan() {
   string ini=File.ReadAllText(System.IO.Path.Combine(root,"DXVK","renderer.ini"));
   Match match=Regex.Match(ini,@"(?im)^\s*Vulkan\s*=\s*([01])\s*$");
   if(!match.Success) throw new InvalidDataException("The renderer setting is missing. Check DXVK\\renderer.ini.");
   return match.Groups[1].Value=="1";
  }
  static bool GameRunning() { return Process.GetProcessesByName("aion.bin").Any() || Process.GetProcessesByName("aion").Any(); }
  void Page(string page) {
   foreach(string name in new string[]{"Overview","Mods","Settings"}) {
    Find<FrameworkElement>(name+"Panel").Visibility=name==page?Visibility.Visible:Visibility.Collapsed;
    Button(name+"Tab").Background=Brush(name==page?"#B8203A48":"#00000000");
    Button(name+"Tab").BorderBrush=Brush(name==page?"#7477998E":"#00000000");
   }
  }
  void SaveSettings() {
   if(busy) return;
   try {
    bool selected=Find<RadioButton>("Vulkan").IsChecked==true;
    if(selected!=ReadVulkan()) {
     if(GameRunning()) throw new InvalidOperationException("Close Aion before changing the renderer.");
     string path=System.IO.Path.Combine(root,"DXVK","renderer.ini");
     string text=File.ReadAllText(path); text=Regex.Replace(text,@"(?im)^(\s*Vulkan\s*=\s*)[01](\s*)$","${1}"+(selected?"1":"0")+"${2}");
     File.WriteAllText(path,text,Encoding.ASCII);
    }
    preferences.Animations=Find<CheckBox>("Animations").IsChecked==true; preferences.CloseAfter=Find<CheckBox>("CloseAfter").IsChecked==true;
    File.WriteAllText(System.IO.Path.Combine(support,"preferences.json"),new JavaScriptSerializer().Serialize(preferences));
    SetMotion();
    Text("SettingsFeedback").Text="Saved. Your next launch will use "+(selected?"Vulkan / DXVK.":"Direct3D 9.");
   } catch(Exception error) { Text("SettingsFeedback").Text=error.Message; }
  }
  void ReadRelease() {
   try {
    ReleaseInfo release=new JavaScriptSerializer().Deserialize<ReleaseInfo>(File.ReadAllText(ClientPath("Mods/release.json")));
    if(release==null || String.IsNullOrWhiteSpace(release.Product) || !Regex.IsMatch(release.Version??"",@"^\d+\.\d+(?:\.\d+)?$")) throw new InvalidDataException("Invalid release metadata.");
    Text("ReleaseLabel").Text="  "+release.Product+" "+release.Version;
   } catch(Exception error) {
    if(!(error is IOException || error is UnauthorizedAccessException || error is ArgumentException || error is InvalidOperationException))throw;
    Text("ReleaseLabel").Text="  Release information unavailable";
   }
  }
  static string FolderTitle(string name) { return Regex.Replace(name,@"(?<=[a-z0-9])(?=[A-Z])"," "); }
  ModInfo ReadModInfo(string folder) {
   string path=System.IO.Path.Combine(folder,"mod.json"), title=FolderTitle(System.IO.Path.GetFileName(folder));
   ModInfo info=null;bool changed=false, writable=true;
   try { if(File.Exists(path)) info=new JavaScriptSerializer().Deserialize<ModInfo>(File.ReadAllText(path)); }
   catch(Exception error) {
    if(!(error is IOException || error is UnauthorizedAccessException || error is ArgumentException || error is InvalidOperationException))throw;writable=false;}
   if(info==null) { info=new ModInfo(); changed=!File.Exists(path); }
   if(String.IsNullOrWhiteSpace(info.Title)) { info.Title=title; changed=true; }
   if(String.IsNullOrWhiteSpace(info.Description)) {
    info.Description="Client features provided by "+title+".";
    foreach(string readme in new string[]{"README.txt","README.md"}) {
     string candidate=System.IO.Path.Combine(folder,readme);
     if(!File.Exists(candidate))continue;
     try {
      string line=File.ReadLines(candidate).Select(x=>x.Trim()).FirstOrDefault(x=>x.Length>25 && !x.StartsWith("#") && !x.StartsWith("`") && !x.StartsWith("  "));
      if(line!=null)info.Description=line.Length>240?line.Substring(0,237)+"...":line;
     } catch(IOException) {} catch(UnauthorizedAccessException) {}
     break;
    }
    changed=true;
   }
   if(changed && writable) try { File.WriteAllText(path,new JavaScriptSerializer().Serialize(info)); } catch(IOException) {} catch(UnauthorizedAccessException) {}
   return info;
  }
  void AddMods() {
   ModInventory installed=new ModInventory();
   try { installed=new JavaScriptSerializer().Deserialize<ModInventory>(File.ReadAllText(ClientPath("Mods/InstalledMods.json")))??installed; }
   catch(Exception error) {
    if(!(error is IOException || error is UnauthorizedAccessException || error is ArgumentException || error is InvalidOperationException))throw;}
   StackPanel list=Find<StackPanel>("ModList");list.Children.Clear();
   foreach(string folder in Directory.GetDirectories(ClientPath("Mods")).OrderBy(x=>x,StringComparer.OrdinalIgnoreCase)) {
    string name=System.IO.Path.GetFileName(folder);
    if(name.Equals("Backups",StringComparison.OrdinalIgnoreCase) || (File.GetAttributes(folder)&FileAttributes.ReparsePoint)!=0)continue;
    ModInfo info=ReadModInfo(folder);if(!info.Listed)continue;
    HashSet<string> required=new HashSet<string>(info.Files??new string[0],StringComparer.OrdinalIgnoreCase);
    List<InstalledFile> files;
    if(installed.features!=null && installed.features.TryGetValue(name,out files))
     foreach(InstalledFile file in files)if(file!=null && !String.IsNullOrEmpty(file.installedSha256))required.Add(file.path);
    List<string> missing=new List<string>();
    foreach(string relative in required) {
     try { if(!File.Exists(ClientPath(relative)))missing.Add(relative); }
     catch(ArgumentException) { missing.Add("Invalid mod file path"); } catch(InvalidDataException) { missing.Add("Invalid mod file path"); }
    }
    if(required.Count==0 && !Directory.GetFiles(folder).Any(x=>!System.IO.Path.GetFileName(x).Equals("mod.json",StringComparison.OrdinalIgnoreCase)
      && !System.IO.Path.GetFileName(x).StartsWith("README",StringComparison.OrdinalIgnoreCase)) && Directory.GetDirectories(folder).Length==0)
     missing.Add("No mod files found");
    StackPanel row=new StackPanel { Margin=new Thickness(0,0,10,14) };
    row.Children.Add(new TextBlock { Text="✧  "+info.Title,FontSize=14,Foreground=Brush("#EDD6A6") });
    row.Children.Add(new TextBlock { Text=info.Description,FontSize=12,Foreground=Brush("#C4D7E1"),Margin=new Thickness(20,4,0,0),TextWrapping=TextWrapping.Wrap });
    if(missing.Count>0)row.Children.Add(new TextBlock { Text="Missing mod files: "+String.Join(", ",missing),FontSize=11,Foreground=Brush("#E5AD87"),Margin=new Thickness(20,4,0,0),TextWrapping=TextWrapping.Wrap });
    list.Children.Add(row);
   }
  }
  void AddParticles() {
   Canvas canvas=Find<Canvas>("Motes");
   for(int i=0;i<32;i++) {
    double size=random.NextDouble()*2+1;
    Ellipse mote=new Ellipse { Width=size,Height=size,Fill=Brush(i%3==0?"#A6E5F4":"#F0D7A1"),Opacity=random.NextDouble()*.4+.1,IsHitTestVisible=false };
    Canvas.SetLeft(mote,random.NextDouble()*1120); Canvas.SetTop(mote,80+random.NextDouble()*580);
    particles.Add(mote); canvas.Children.Add(mote);
   }
  }
  void SetMotion() {
   System.Windows.Media.Effects.DropShadowEffect glow=Find<System.Windows.Media.Effects.DropShadowEffect>("PlayGlow");
   if(glow==null) return;
   glow.BeginAnimation(System.Windows.Media.Effects.DropShadowEffect.OpacityProperty,null);
   if(preferences.Animations) {
    DoubleAnimation pulse=new DoubleAnimation(.18,.48,TimeSpan.FromSeconds(2.8)); pulse.AutoReverse=true; pulse.RepeatBehavior=RepeatBehavior.Forever;
    glow.BeginAnimation(System.Windows.Media.Effects.DropShadowEffect.OpacityProperty,pulse);
   } else glow.Opacity=.24;
  }
  void Animate() {
   if(window.WindowState==WindowState.Minimized) return;
   Find<Canvas>("Motes").Visibility=preferences.Animations?Visibility.Visible:Visibility.Hidden;
   if(preferences.Animations) for(int i=0;i<particles.Count;i++) {
    Ellipse mote=particles[i]; double y=Canvas.GetTop(mote)-(.10+i%4*.035), x=Canvas.GetLeft(mote)+Math.Sin(ticks*.009+i)*.06;
    if(y<30) y=650; Canvas.SetTop(mote,y); Canvas.SetLeft(mote,x);
   }
   if(busy) { Find<Rectangle>("ProgressShine").Opacity=1; ((TranslateTransform)Find<Rectangle>("ProgressShine").RenderTransform).X=(ticks*6%700)-90; }
   else Find<Rectangle>("ProgressShine").Opacity=0;
  }
  async Task PollServer() {
   if(polling || closed) return; polling=true; bool online=false, known=false;
   try {
    // Passive local listener lookup: do not create empty login connections.
    if(host=="127.0.0.1" || host=="localhost" || host=="::1") {
     online=await Task.Run(()=>IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners().Any(p=>p.Port==port)); known=true;
    }
   } catch(Exception) {} finally { polling=false; }
   if(closed) return;
   Text("ServerStatus").Text=known?(online?"Login port open":"Login port closed"):"Remote server";
   Find<Ellipse>("ServerDot").Fill=Brush(online?"#9CDEC3":"#E0B580");
  }
  string ClientPath(string relative) {
   string path=System.IO.Path.GetFullPath(System.IO.Path.Combine(root,relative));
   if(!path.StartsWith(root+System.IO.Path.DirectorySeparatorChar,StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException("A launcher path is outside this client.");
   return path;
  }
  async Task RunScript(Step step) {
   string path=step.Script=="@PortablePreparation"?PortablePreparation():ClientPath(step.Script);
   if(path==null || !File.Exists(path)) throw new FileNotFoundException("A mod startup helper is missing: "+step.Script);
   string args="-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "+Quote(path)+" -ClientPath "+Quote(root);
   foreach(string arg in step.Arguments) args+=" "+(arg.StartsWith("-")?arg:Quote(arg));
   string powershell=System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows),"System32","WindowsPowerShell","v1.0","powershell.exe");
   ProcessStartInfo start=new ProcessStartInfo(powershell,args) { WorkingDirectory=root,UseShellExecute=false,CreateNoWindow=true,WindowStyle=ProcessWindowStyle.Hidden,RedirectStandardOutput=true,RedirectStandardError=true };
   start.EnvironmentVariables["PSModulePath"]=System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows),"System32","WindowsPowerShell","v1.0","Modules");
   using(Process process=new Process { StartInfo=start }) {
    process.Start(); Task<string> stdout=process.StandardOutput.ReadToEndAsync(), stderr=process.StandardError.ReadToEndAsync();
    await Task.Run(()=>process.WaitForExit()); string output=await stdout, errors=await stderr;
    File.AppendAllText(logPath,"\r\n"+DateTime.Now.ToString("HH:mm:ss")+" "+step.Title+"\r\n"+output+errors);
    if(process.ExitCode!=0) {
     string last=errors.Split(new char[]{'\r','\n'},StringSplitOptions.RemoveEmptyEntries).FirstOrDefault()??"The startup helper returned an error.";
     throw new InvalidOperationException(last.Length>180?last.Substring(0,180):last);
    }
   }
  }
  string PortablePreparation() {
   string package=Directory.GetParent(root).FullName;
   if(!File.Exists(System.IO.Path.Combine(package,"REPACK.json")))return null;
   if(!String.Equals(root,System.IO.Path.Combine(package,"Client"),StringComparison.OrdinalIgnoreCase))
    throw new InvalidOperationException("The portable launcher must remain in this repack's Client folder.");
   string helper=System.IO.Path.Combine(package,"Control","Prepare-Client.ps1");
   if(!File.Exists(helper))throw new FileNotFoundException("The repack's client preparation helper is missing.",helper);
   return helper;
  }
  List<Step> Steps() {
   List<Step> steps=new List<Step>();
   if(PortablePreparation()!=null)steps.Add(new Step("Preparing portable client paths","@PortablePreparation"));
   steps.Add(new Step("Preparing the Graphics menu","Mods/GraphicsMenu/Install-GraphicsMenu.ps1"));
   steps.Add(new Step("Selecting your renderer","Mods/Renderer/SelectRenderer.ps1"));
   steps.Add(new Step("Preparing the native cursor","Mods/CursorFix/Install-CursorFix.ps1"));
   steps.Add(new Step("Applying graphics settings","Mods/Renderer/QualityProfile.ps1","-Startup"));
   steps.Add(new Step("Enabling client flight","Mods/FreeFlight/Install-FreeFlight.ps1"));
   string queuePath=ClientPath("Mods/GraphicsOverhaul/queued.json");
   if(File.Exists(queuePath)) {
    Dictionary<string,object> queue=new JavaScriptSerializer().Deserialize<Dictionary<string,object>>(File.ReadAllText(queuePath));
    string id=Convert.ToString(queue["packageId"],CultureInfo.InvariantCulture);
    if(!Regex.IsMatch(id,"^[a-f0-9]{16}$")) throw new InvalidDataException("The graphics package name is invalid.");
    steps.Add(new Step("Preparing world artwork","Mods/GraphicsOverhaul/Install-GraphicsOverhaul.ps1","-PackagePath",ClientPath("Mods/GraphicsOverhaul/packages/"+id)));
   }
   return steps;
  }
  async Task Launch() {
   if(busy) return; busy=true; closeRequested=false; Button("Play").IsEnabled=false; Button("SaveSettings").IsEnabled=false;
   LoadingDisplay(true); Button("OpenLog").Visibility=Visibility.Collapsed;
   try {
    ReadGameArguments(); string logs=ClientPath("Mods/Launcher/Logs"); Directory.CreateDirectory(logs);
    logPath=System.IO.Path.Combine(logs,"launch-"+DateTime.Now.ToString("yyyyMMdd-HHmmss-fff")+".log");
    File.WriteAllText(logPath,"Aion Aetherfall launcher\r\nClient: "+root+"\r\n");
    if(GameRunning()) throw new InvalidOperationException("Aion is already running.");
    if(File.Exists(ClientPath("bin64/game.dll.patched"))) throw new InvalidOperationException("A pending Game.dll update needs to be installed before launch. Open the client folder and finish that update.");
    if(!File.Exists(ClientPath("bin64/aion.bin"))) throw new FileNotFoundException("The 64-bit Aion client was not found.");
    List<Step> steps=Steps(); double width=Find<Grid>("ProgressTrack").ActualWidth;
    for(int i=0;i<steps.Count;i++) {
     if(closeRequested) { busy=false; window.Close(); return; }
     await RunScript(steps[i]);
     Find<Border>("ProgressFill").BeginAnimation(FrameworkElement.WidthProperty,new DoubleAnimation(.6*(i+1)*width/steps.Count,TimeSpan.FromMilliseconds(250)));
    }
    if(closeRequested) { busy=false; window.Close(); return; }
    ProcessStartInfo start=new ProcessStartInfo(ClientPath("bin64/aion.bin"),gameArgs) { WorkingDirectory=root,UseShellExecute=false,CreateNoWindow=true,WindowStyle=ProcessWindowStyle.Hidden };
    start.EnvironmentVariables["DXVK_CONFIG_FILE"]=ClientPath("dxvk.conf");
    string rendererLogs=ClientPath("Mods/Renderer/logs"); Directory.CreateDirectory(rendererLogs); start.EnvironmentVariables["DXVK_LOG_PATH"]=rendererLogs;
    if(ReadVulkan()) {
     string layer=ClientPath("Mods/GraphicsOverhaul/vulkan64"), previous=start.EnvironmentVariables["VK_LAYER_PATH"];
     start.EnvironmentVariables["VK_LAYER_PATH"]=layer+(String.IsNullOrEmpty(previous)?"":";"+previous);
     previous=start.EnvironmentVariables["VK_INSTANCE_LAYERS"];
     start.EnvironmentVariables["VK_INSTANCE_LAYERS"]="VK_LAYER_reshade"+(String.IsNullOrEmpty(previous)?"":";"+previous);
     start.EnvironmentVariables["RESHADE_BASE_PATH_OVERRIDE"]=ClientPath("Mods/GraphicsOverhaul/PostFX");
    }
    string readyName="Local\\AetherfallLoginReady-"+Guid.NewGuid().ToString("N");
    using(EventWaitHandle loginReady=new EventWaitHandle(false,EventResetMode.ManualReset,readyName))
    using(EventWaitHandle showClient=new EventWaitHandle(false,EventResetMode.ManualReset,readyName+"-Show")) {
    start.EnvironmentVariables["AETHERFALL_STARTUP_GATE"]=readyName+"-Show";
    start.EnvironmentVariables["AETHERFALL_STARTUP_TRACE"]=logPath+".presentation.log";
    start.EnvironmentVariables["AETHERFALL_LAUNCHER_PID"]=Process.GetCurrentProcess().Id.ToString(CultureInfo.InvariantCulture);
    try {
    start.EnvironmentVariables["AETHERFALL_LOGIN_READY_EVENT"]=readyName;
    using(Process game=Process.Start(start)) {
     File.AppendAllText(logPath,"\r\nGame process created: "+game.Id+"\r\n");
     Stopwatch deadline=Stopwatch.StartNew(); bool windowSeen=false;
     while(!loginReady.WaitOne(0)) {
      await Task.Delay(100); game.Refresh();
      if(game.HasExited) throw new InvalidOperationException("Aion closed during startup (exit "+game.ExitCode+"). See the launch log and client log.txt.");
      if(closeRequested) { busy=false; window.Close(); return; }
      if(deadline.Elapsed>TimeSpan.FromMinutes(4)) throw new InvalidOperationException("The login screen has not reported ready. Check the game window or the launch log.");
      if(!windowSeen && ClientWindow.Find(game.Id)!=IntPtr.Zero) {
       windowSeen=true; Find<Border>("ProgressFill").BeginAnimation(FrameworkElement.WidthProperty,new DoubleAnimation(width*.85,TimeSpan.FromMilliseconds(400)));
      }
     }
     File.AppendAllText(logPath,"\r\nNative login screen ready: "+deadline.ElapsedMilliseconds+" ms after process start.\r\n");
     Find<Border>("ProgressFill").BeginAnimation(FrameworkElement.WidthProperty,new DoubleAnimation(width,TimeSpan.FromMilliseconds(300)));
     await Task.Delay(300);
     bool giveFocus=GetForegroundWindow()==new System.Windows.Interop.WindowInteropHelper(window).Handle;
     if(giveFocus)AllowSetForegroundWindow((uint)game.Id);
     showClient.Set(); // Reveal only after native login readiness, never HWND creation.
     window.Topmost=false;
     IntPtr loginWindow=IntPtr.Zero;
     Stopwatch revealDeadline=Stopwatch.StartNew();
     while(revealDeadline.Elapsed<TimeSpan.FromSeconds(5)) {
      await Task.Delay(100);game.Refresh();
      if(game.HasExited)throw new InvalidOperationException("Aion closed during the login handoff. See the launch log.");
      loginWindow=ClientWindow.Find(game.Id);
      if(loginWindow!=IntPtr.Zero && ClientWindow.IsWindowVisible(loginWindow))break;
     }
     if(loginWindow==IntPtr.Zero || !ClientWindow.IsWindowVisible(loginWindow))
      throw new InvalidOperationException("Login is ready but the game window has not acknowledged the handoff. See the presentation log.");
     File.AppendAllText(logPath,"Native login window visible after the ready handoff.\r\n");
     if(giveFocus && GetForegroundWindow()==new System.Windows.Interop.WindowInteropHelper(window).Handle)SetForegroundWindow(loginWindow);
    }
    } finally { showClient.Set(); } // Errors/cancellation must never strand a hidden game.
    }
    Find<Border>("ProgressFill").BeginAnimation(FrameworkElement.WidthProperty,new DoubleAnimation(width,TimeSpan.FromMilliseconds(300)));
    Text("StatusTitle").Text="Ready to log in."; Text("StatusDetail").Text="The login screen is ready."; Text("StageCount").Text="";
    Text("LaunchCaption").Text="AION IS RUNNING";
    busy=false; if(preferences.CloseAfter) window.Close(); else window.WindowState=WindowState.Minimized;
   } catch(Exception error) {
    Text("StatusTitle").Text="Launch paused"; Text("StatusDetail").Text=error.Message; Text("StageCount").Text="ACTION NEEDED";
    if(logPath!=null) { try { File.AppendAllText(logPath,"\r\nStopped: "+error+"\r\n"); } catch(IOException) {} Button("OpenLog").Visibility=Visibility.Visible; }
   } finally {
    busy=false; LoadingDisplay(false); Button("Play").IsEnabled=true; Button("SaveSettings").IsEnabled=true;
    if(closeRequested && !closed) window.Close();
   }
  }
  void LoadingDisplay(bool loading) {
   window.Topmost=false;
   Visibility value=loading?Visibility.Hidden:Visibility.Visible;
   Find<StackPanel>("StatusHeading").Visibility=value; Text("StatusTitle").Visibility=value;
   Text("StatusDetail").Visibility=value; Text("LaunchCaption").Visibility=value;
   if(loading) { Find<Border>("ProgressFill").BeginAnimation(FrameworkElement.WidthProperty,null); Find<Border>("ProgressFill").Width=0; }
  }
  static void Open(string path) { try { Process.Start(new ProcessStartInfo(path) { UseShellExecute=true }); } catch(Exception error) { MessageBox.Show(error.Message,"Aion Aetherfall"); } }
  void RenderPreview(string[] args) {
   string destination=null, page="Overview";
   for(int i=0;i+1<args.Length;i++) { if(args[i]=="--preview") destination=args[i+1]; if(args[i]=="--page") page=args[i+1]; }
   if(destination==null) throw new ArgumentException("Provide a preview output file.");
   Page(page); Text("ServerStatus").Text="Login server offline";
   if(args.Contains("--loading")) { LoadingDisplay(true); Find<Border>("ProgressFill").Width=260; }
   scene.Measure(new Size(1180,744)); scene.Arrange(new Rect(0,0,1180,744)); scene.UpdateLayout();
   RenderTargetBitmap bitmap=new RenderTargetBitmap(1180,744,96,96,PixelFormats.Pbgra32); bitmap.Render(scene);
   PngBitmapEncoder encoder=new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
   using(FileStream file=File.Create(destination)) encoder.Save(file);
   mutex.ReleaseMutex(); mutex.Dispose();
  }
 }
}
