using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Reflection;
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
using Microsoft.Win32;

namespace AetherfallSetup {
 public sealed class SetupSettings {
  public string MirrorsUrl = "";
  public string UpdateUrl = "";
  public string Mode = "Auto";
  public string DownloadDirectory = "Downloads";
  public string InstallDirectory = "Games";
  public bool DeleteArchiveAfter = false;
 }
 public sealed class ReleaseFile { public string name, sha256; public long bytes; }
 public sealed class ReleaseManifest {
  public int schema = 1;
  public string product, version = "0.86", installFolder = "Aetherfall-0.86", torrentFolder = "", torrentFile = "Aetherfall-0.86.torrent", torrentSha256, infoHash, packageFilesIndexSha256;
  public long archiveBytes, installedBytes;
  public List<ReleaseFile> files = new List<ReleaseFile>();
 }
 public sealed class MirrorConfig {
  public int schema = 1;
  public string release = "0.86";
  public List<string> WebSeeds = new List<string>();
  public Dictionary<string, List<string>> Files = new Dictionary<string, List<string>>();
  public List<string> SharePages = new List<string>();
 }
 public sealed class ReleaseJson { public string Product, Version; }

 public sealed class SetupApp {
  Window window; Grid scene;
  string root, customArchivePath;
  bool busy, closed, preview;
  CancellationTokenSource downloadCts;
  SetupSettings settings = new SetupSettings();
  ReleaseManifest manifest = new ReleaseManifest();
  DispatcherTimer timer;
  readonly List<Ellipse> particles = new List<Ellipse>();
  readonly Random random = new Random(86);
  int ticks;

  T Find<T>(string name) where T : class { return window.FindName(name) as T; }
  TextBlock Text(string name) { return Find<TextBlock>(name); }
  Button Btn(string name) { return Find<Button>(name); }
  static SolidColorBrush Brush(string color) { return (SolidColorBrush)new BrushConverter().ConvertFromString(color); }
  static string Quote(string v) { return "\"" + v.Replace("\"", "\\\"") + "\""; }

  [STAThread]
  public static int Main(string[] args) {
   try {
    ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
    ServicePointManager.DefaultConnectionLimit = 32;
    Application app = new Application { ShutdownMode = ShutdownMode.OnMainWindowClose };
    SetupApp setup = new SetupApp();
    setup.Initialize(args);
    if (setup.preview) { setup.RenderPreview(args); return 0; }
    app.Run(setup.window);
    return 0;
   } catch (Exception ex) {
    MessageBox.Show("Aetherfall Setup encountered an error:\n\n" + ex.Message, "Aion Aetherfall", MessageBoxButton.OK, MessageBoxImage.Error);
    return 1;
   }
  }

  void Initialize(string[] args) {
   root = AppDomain.CurrentDomain.BaseDirectory.TrimEnd(System.IO.Path.DirectorySeparatorChar);
   preview = args.Contains("--preview");
   using (Stream s = Assembly.GetExecutingAssembly().GetManifestResourceStream("Aetherfall.SetupWindow.xaml"))
    window = (Window)XamlReader.Load(s);
   scene = Find<Grid>("Scene");
   Find<Image>("Hero").Source = LoadResourceImage("Aetherfall.atreia.png");
   Find<Image>("Frame").Source = LoadResourceImage("Aetherfall.frame.png");
   Find<Image>("AionLogo").Source = new CroppedBitmap(LoadResourceImage("Aetherfall.aion-native-atlas.png"), new Int32Rect(0, 272, 624, 162));
   Find<Image>("AetherfallLogo").Source = new CroppedBitmap(LoadResourceImage("Aetherfall.aetherfall-logo.png"), new Int32Rect(0, 146, 2048, 460));

   double availableW = SystemParameters.WorkArea.Width - 35, availableH = SystemParameters.WorkArea.Height - 35;
   double scale = Math.Min(1, Math.Min(availableW / 1180, availableH / 744));
   window.Width = 1180 * scale; window.Height = 744 * scale;

   LoadConfig();
   Find<Border>("DragArea").MouseLeftButtonDown += delegate { if (Mouse.LeftButton == MouseButtonState.Pressed) window.DragMove(); };
   Btn("Minimize").Click += delegate { window.WindowState = WindowState.Minimized; };
   Btn("Exit").Click += delegate {
    if (downloadCts != null) { try { downloadCts.Cancel(); } catch {} }
    window.Close();
   };
   Btn("HubTab").Click += delegate { SwitchTab("Hub"); };
   Btn("ServersTab").Click += delegate { SwitchTab("Servers"); };
   Btn("PathsTab").Click += delegate { SwitchTab("Paths"); };
   Btn("OpenInstallFolder").Click += delegate { OpenFolder(ResolveInstallPath()); };
   Btn("BrowseInstall").Click += delegate { PickInstallFolder(); };
   Btn("SelectArchiveBtn").Click += delegate { PickLocalArchive(); };
   Btn("UpdateNowBtn").Click += async delegate { await RunUpdate(null); };
   Btn("ApplyPatchFileBtn").Click += async delegate { await PickAndApplyPatch(); };
   Btn("SavePathsBtn").Click += delegate { SaveSettingsFromUi(); };
   Btn("PrimaryBtn").Click += async delegate { await OnPrimaryAction(); };
   Btn("StartAllServerBtn").Click += async delegate { await RunServerControl("StartAll"); };
   Btn("StartDbOnlyBtn").Click += async delegate { await RunServerControl("StartDb"); };
   Btn("StartGsOnlyBtn").Click += async delegate { await RunServerControl("StartGs"); };
   Btn("StopAllServerBtn").Click += async delegate { await RunServerControl("StopDb"); };

   window.Loaded += delegate { RefreshState(); };
   window.Closed += delegate {
    closed = true;
    if (downloadCts != null) { try { downloadCts.Cancel(); } catch {} }
    if (timer != null) timer.Stop();
   };
   AddParticles();
   StartGlow();
   RefreshState();
   timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(50) };
   timer.Tick += async delegate { Animate(); if (++ticks % 60 == 1) await PollPorts(); };
   if (!preview) timer.Start();
  }

  static BitmapImage LoadResourceImage(string resourceName) {
   using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(resourceName)) {
    BitmapImage img = new BitmapImage();
    img.BeginInit();
    img.CacheOption = BitmapCacheOption.OnLoad;
    img.StreamSource = stream;
    img.EndInit();
    img.Freeze();
    return img;
   }
  }

  void LoadConfig() {
   JavaScriptSerializer js = new JavaScriptSerializer();
   string settingsPath = System.IO.Path.Combine(root, "settings.json");
   if (File.Exists(settingsPath)) {
    try { settings = js.Deserialize<SetupSettings>(File.ReadAllText(settingsPath)) ?? settings; } catch {}
   }
   string manifestPath = System.IO.Path.Combine(root, "release-manifest.json");
   if (File.Exists(manifestPath)) {
    try { manifest = js.Deserialize<ReleaseManifest>(File.ReadAllText(manifestPath)) ?? manifest; } catch {}
   }
   Find<CheckBox>("DeleteArchiveAfter").IsChecked = settings.DeleteArchiveAfter;
   Find<RadioButton>("ModeAuto").IsChecked = settings.Mode == "Auto";
   Find<RadioButton>("ModeTorrent").IsChecked = settings.Mode == "Torrent";
   Find<RadioButton>("ModeHttp").IsChecked = settings.Mode == "Http";
   Find<TextBox>("UpdateUrlBox").Text = settings.UpdateUrl ?? "";
  }

  void SaveSettingsFromUi() {
   if (Find<RadioButton>("ModeTorrent").IsChecked == true) settings.Mode = "Torrent";
   else if (Find<RadioButton>("ModeHttp").IsChecked == true) settings.Mode = "Http";
   else settings.Mode = "Auto";
   settings.DeleteArchiveAfter = Find<CheckBox>("DeleteArchiveAfter").IsChecked == true;
   settings.UpdateUrl = (Find<TextBox>("UpdateUrlBox").Text ?? "").Trim();
   try {
    File.WriteAllText(System.IO.Path.Combine(root, "settings.json"), new JavaScriptSerializer().Serialize(settings));
    string pkg = ResolveInstallPath();
    string launcherUpdate = System.IO.Path.Combine(pkg, "Client", "Mods", "Launcher", "update.json");
    if (Directory.Exists(System.IO.Path.GetDirectoryName(launcherUpdate)))
     File.WriteAllText(launcherUpdate, new JavaScriptSerializer().Serialize(new { UpdateUrl = settings.UpdateUrl }));
    Text("PathsFeedback").Text = "Settings saved.";
   } catch (Exception ex) {
    Text("PathsFeedback").Text = ex.Message;
   }
   RefreshState();
  }

  string ResolveDownloadDir() {
   return System.IO.Path.IsPathRooted(settings.DownloadDirectory)
    ? System.IO.Path.GetFullPath(settings.DownloadDirectory)
    : System.IO.Path.GetFullPath(System.IO.Path.Combine(root, settings.DownloadDirectory));
  }

  string ResolveInstallPath() {
   if (File.Exists(System.IO.Path.Combine(root, "REPACK.json"))) return root;
   string parent = Directory.GetParent(root) != null ? Directory.GetParent(root).FullName : root;
   if (File.Exists(System.IO.Path.Combine(parent, "REPACK.json"))) return parent;
   string installBase = System.IO.Path.IsPathRooted(settings.InstallDirectory)
    ? System.IO.Path.GetFullPath(settings.InstallDirectory)
    : System.IO.Path.GetFullPath(System.IO.Path.Combine(root, settings.InstallDirectory));
   return System.IO.Path.Combine(installBase, manifest.installFolder ?? "Aetherfall-0.86");
  }

  bool IsInstalled() {
   string pkg = ResolveInstallPath();
   return !File.Exists(System.IO.Path.Combine(pkg, ".aetherfall-extracting")) &&
          File.Exists(System.IO.Path.Combine(pkg, "REPACK.json")) &&
          File.Exists(System.IO.Path.Combine(pkg, "Client", "Aetherfall Launcher.exe")) &&
          File.Exists(System.IO.Path.Combine(pkg, "Server", "gameserver", "AL-Game.jar"));
  }

  string InstalledVersion() {
   string pkg = ResolveInstallPath();
   string rel = System.IO.Path.Combine(pkg, "release.json");
   if (File.Exists(rel)) {
    try {
     ReleaseJson r = new JavaScriptSerializer().Deserialize<ReleaseJson>(File.ReadAllText(rel));
     if (r != null && !String.IsNullOrWhiteSpace(r.Version)) return r.Version;
    } catch {}
   }
   return manifest.version ?? "0.86";
  }

  string FindCompleteLocalArchive() {
   if (!String.IsNullOrEmpty(customArchivePath) && File.Exists(customArchivePath)) return customArchivePath;
   ReleaseFile rf = (manifest.files != null && manifest.files.Count > 0) ? manifest.files[0] : null;
   string archiveName = rf != null ? rf.name : ("Aetherfall-" + (manifest.version ?? "0.86") + ".7z");
   long expectedBytes = rf != null ? rf.bytes : 39030688343L;
   string dlRoot = ResolveDownloadDir();
   foreach (string dir in new[] { dlRoot, root }) {
    string candidate = System.IO.Path.Combine(dir, archiveName);
    string partmap = candidate + ".partmap";
    if (File.Exists(candidate) && !File.Exists(partmap) && new FileInfo(candidate).Length == expectedBytes)
     return candidate;
   }
   return null;
  }

  bool GetPartialDownloadProgress(out long doneBytes, out long totalBytes, out double pct) {
   doneBytes = 0;
   ReleaseFile rf = (manifest.files != null && manifest.files.Count > 0) ? manifest.files[0] : null;
   totalBytes = rf != null ? rf.bytes : 39030688343L;
   pct = 0;
   string archiveName = rf != null ? rf.name : ("Aetherfall-" + (manifest.version ?? "0.86") + ".7z");
   const int chunkSize = 8 * 1024 * 1024;
   int totalChunks = (int)((totalBytes + chunkSize - 1) / chunkSize);

   foreach (string dir in new[] { ResolveDownloadDir(), root }) {
    string arc = System.IO.Path.Combine(dir, archiveName);
    string map = arc + ".partmap";
    if (File.Exists(arc) && File.Exists(map)) {
     try {
      byte[] flags = File.ReadAllBytes(map);
      if (flags.Length == totalChunks) {
       for (int i = 0; i < totalChunks; i++) {
        if (flags[i] == 1) {
         doneBytes += Math.Min((long)chunkSize, totalBytes - (long)i * chunkSize);
        }
       }
       if (doneBytes > 0 && doneBytes < totalBytes) {
        pct = (doneBytes * 100.0) / totalBytes;
        return true;
       }
      }
     } catch {}
    }
   }
   return false;
  }

  void RefreshState() {
   string pkg = ResolveInstallPath();
   string ver = InstalledVersion();
   bool installed = IsInstalled();
   Text("InstallPathLabel").Text = pkg;
   Text("ReleaseLabel").Text = "  REPACK HUB & AUTO-UPDATER  ·  " + ver;
   string localArc = FindCompleteLocalArchive();
   long partDone, partTotal; double partPct;
   bool hasPartial = GetPartialDownloadProgress(out partDone, out partTotal, out partPct);
   bool interruptedExtract = File.Exists(System.IO.Path.Combine(pkg, ".aetherfall-extracting"));

   if (installed) {
    Find<Ellipse>("InstallDot").Fill = Brush("#9CDEC3");
    Text("InstallBadge").Text = "Installed (v" + ver + ")";
    Text("PrimaryLabel").Text = "PLAY";
    Text("LaunchCaption").Text = "OPEN AETHERFALL GAME LAUNCHER";
    Text("HeroHeadline").Text = "Aetherfall " + ver + " is Installed.";
    Text("ArchiveSourceLabel").Text = "Click 'Update Repack' anytime to apply a new patch ZIP without losing characters.";
    if (!busy) {
     Text("StatusTitle").Text = "Ready for Atreia.";
     Text("StatusDetail").Text = "Use Server Control to start MariaDB & GameServer, or click PLAY to open the game launcher.";
    }
   } else {
    Find<Ellipse>("InstallDot").Fill = Brush("#E0B580");
    if (!busy) {
     if (localArc != null && interruptedExtract) {
      Text("InstallBadge").Text = "Extraction paused";
      Text("PrimaryLabel").Text = "RESUME";
      Text("LaunchCaption").Text = "RESUME UNPACKING REPACK (" + ver + ")";
      Text("ArchiveSourceLabel").Text = "Complete archive verified: " + localArc;
      Text("StatusTitle").Text = "Extraction paused — Ready to resume.";
      Text("StatusDetail").Text = "Click RESUME to finish unpacking " + System.IO.Path.GetFileName(localArc) + " (no re-download needed).";
     } else if (hasPartial) {
      string pctStr = partPct.ToString("0.0") + "%  (" + FormatGb(partDone) + " / " + FormatGb(partTotal) + ")";
      Text("InstallBadge").Text = "Paused (" + partPct.ToString("0.0") + "%)";
      Text("PrimaryLabel").Text = "RESUME";
      Text("LaunchCaption").Text = "RESUME DOWNLOAD WHERE YOU LEFT OFF";
      Text("ArchiveSourceLabel").Text = "Saved progress: " + FormatGb(partDone) + " of " + FormatGb(partTotal) + " on disk";
      Text("StatusTitle").Text = "Download paused at " + partPct.ToString("0.0") + "%";
      Text("StatusDetail").Text = "Click RESUME to continue downloading from " + FormatGb(partDone) + " / " + FormatGb(partTotal) + ".";
      SetProgress(partDone / (double)partTotal, pctStr, "⏸ Paused · Progress saved");
     } else if (localArc != null) {
      Text("InstallBadge").Text = "Archive ready";
      Text("PrimaryLabel").Text = "INSTALL";
      Text("LaunchCaption").Text = "VERIFY & EXTRACT REPACK (" + ver + ")";
      Text("ArchiveSourceLabel").Text = "Complete archive ready: " + localArc;
      Text("StatusTitle").Text = "Archive found — Ready to extract.";
      Text("StatusDetail").Text = "Click INSTALL to verify and extract " + System.IO.Path.GetFileName(localArc) + " into " + pkg;
     } else {
      Text("InstallBadge").Text = "Not installed yet";
      Text("PrimaryLabel").Text = "INSTALL";
      Text("LaunchCaption").Text = "DOWNLOAD & EXTRACT REPACK (" + ver + ")";
      Text("ArchiveSourceLabel").Text = "Auto: Multi-Stream Google Drive CDN + BitTorrent P2P Fallback";
      Text("StatusTitle").Text = "Ready to download & install.";
      Text("StatusDetail").Text = "Click INSTALL to download Aetherfall " + ver + " (36.35 GB) with live speed & auto-resume.";
     }
    }
   }
  }

  void SwitchTab(string tab) {
   foreach (string name in new[] { "Hub", "Servers", "Paths" }) {
    Find<FrameworkElement>(name + "Panel").Visibility = name == tab ? Visibility.Visible : Visibility.Collapsed;
    Btn(name + "Tab").Background = Brush(name == tab ? "#B8252C4E" : "#00000000");
    Btn(name + "Tab").BorderBrush = Brush(name == tab ? "#749B8E6E" : "#00000000");
   }
  }

  void PickInstallFolder() {
   if (busy) return;
   var dlg = new SaveFileDialog {
    Title = "Choose where to place the Aetherfall folder",
    FileName = manifest.installFolder ?? "Aetherfall-0.86",
    Filter = "Folder|*.folder",
    CheckPathExists = true
   };
   if (dlg.ShowDialog() == true) {
    string parent = System.IO.Path.GetDirectoryName(dlg.FileName);
    if (!String.IsNullOrEmpty(parent)) {
     settings.InstallDirectory = parent;
     SaveSettingsFromUi();
    }
   }
  }

  void PickLocalArchive() {
   if (busy) return;
   var dlg = new OpenFileDialog {
    Title = "Select downloaded Aetherfall .7z archive",
    Filter = "7-Zip Archive (*.7z;*.7z.001)|*.7z;*.7z.001|All Files (*.*)|*.*"
   };
   if (dlg.ShowDialog() == true) {
    customArchivePath = dlg.FileName;
    RefreshState();
   }
  }

  async Task PickAndApplyPatch() {
   if (busy) return;
   var dlg = new OpenFileDialog {
    Title = "Select Aetherfall-Patch-<version>.zip file",
    Filter = "Aetherfall Patch ZIP (Aetherfall-Patch-*.zip;*.zip)|Aetherfall-Patch-*.zip;*.zip"
   };
   if (dlg.ShowDialog() == true) {
    await RunUpdate(dlg.FileName);
   }
  }

  async Task OnPrimaryAction() {
   if (busy) {
    if (downloadCts != null) {
     Text("StatusDetail").Text = "Pausing and saving exact resume position…";
     downloadCts.Cancel();
    }
    return;
   }
   if (IsInstalled()) {
    string launcherExe = System.IO.Path.Combine(ResolveInstallPath(), "Client", "Aetherfall Launcher.exe");
    if (File.Exists(launcherExe)) {
     Process.Start(new ProcessStartInfo(launcherExe) { WorkingDirectory = System.IO.Path.GetDirectoryName(launcherExe), UseShellExecute = true });
     return;
    }
   }
   await RunInstall();
  }

  void SetBusy(bool value, string banner, string title, string detail, bool allowPause = false) {
   busy = value;
   Btn("PrimaryBtn").IsEnabled = !value || allowPause;
   if (value && allowPause) {
    Text("PrimaryLabel").Text = "PAUSE";
   }
   Btn("UpdateNowBtn").IsEnabled = !value;
   Btn("SelectArchiveBtn").IsEnabled = !value;
   Btn("BrowseInstall").IsEnabled = !value;
   Text("StatusBanner").Text = banner;
   Text("StatusTitle").Text = title;
   Text("StatusDetail").Text = detail;
   if (!value) {
    Text("StageCount").Text = "";
   }
  }

  void SetProgress(double ratio, string percentText = null, string speedText = null) {
   double trackW = Find<Grid>("ProgressTrack").ActualWidth;
   if (trackW <= 10) trackW = 635;
   double clamped = Math.Max(0, Math.Min(1.0, ratio));
   double target = clamped * trackW;
   Border fill = Find<Border>("ProgressFill");
   fill.BeginAnimation(FrameworkElement.WidthProperty, null);
   fill.Width = target;
   if (percentText != null) Text("ProgressPercentLabel").Text = percentText;
   if (speedText != null) Text("StageCount").Text = speedText;
  }

  static string FormatGb(long bytes) {
   return (bytes / (1024.0 * 1024.0 * 1024.0)).ToString("0.00") + " GB";
  }

  static string FormatEta(double seconds) {
   if (double.IsNaN(seconds) || double.IsInfinity(seconds) || seconds <= 0 || seconds > 86400 * 3) return "calculating…";
   TimeSpan ts = TimeSpan.FromSeconds(seconds);
   if (ts.TotalHours >= 1) return string.Format("{0}h {1:00}m", (int)ts.TotalHours, ts.Minutes);
   return string.Format("{0:00}m {1:00}s", ts.Minutes, ts.Seconds);
  }

  MirrorConfig LoadMirrors() {
   JavaScriptSerializer js = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
   MirrorConfig cfg = new MirrorConfig();
   string localPath = System.IO.Path.Combine(root, "mirrors.json");
   if (File.Exists(localPath)) {
    try { cfg = js.Deserialize<MirrorConfig>(File.ReadAllText(localPath)) ?? cfg; } catch {}
   }
   if (!String.IsNullOrWhiteSpace(settings.MirrorsUrl)) {
    try {
     using (var wc = new WebClient { Encoding = Encoding.UTF8 }) {
      wc.Headers.Add("User-Agent", "Mozilla/5.0");
      string raw = wc.DownloadString(settings.MirrorsUrl);
      var remote = js.Deserialize<MirrorConfig>(raw);
      if (remote != null && remote.schema == 1 && remote.release == manifest.version) cfg = remote;
     }
    } catch {}
   }
   return cfg;
  }

  async Task<string> DownloadArchiveBoundedRangesAsync(ReleaseFile fileInfo, string url, string destDir, CancellationToken ct) {
   Directory.CreateDirectory(destDir);
   string archivePath = System.IO.Path.Combine(destDir, fileInfo.name);
   string mapPath = archivePath + ".partmap";
   long totalBytes = fileInfo.bytes;
   const int chunkSize = 8 * 1024 * 1024; // 8 MB per bounded HTTP range request
   int totalChunks = (int)((totalBytes + chunkSize - 1) / chunkSize);

   byte[] completed = new byte[totalChunks];
   if (File.Exists(archivePath) && File.Exists(mapPath)) {
    byte[] saved = File.ReadAllBytes(mapPath);
    if (saved.Length == totalChunks) {
     Array.Copy(saved, completed, totalChunks);
    } else {
     // Repair map length without losing existing flags
     Array.Copy(saved, completed, Math.Min(saved.Length, totalChunks));
     File.WriteAllBytes(mapPath, completed);
    }
    using (var fs = new FileStream(archivePath, FileMode.OpenOrCreate, FileAccess.Write, FileShare.ReadWrite)) {
     if (fs.Length != totalBytes) fs.SetLength(totalBytes);
    }
   } else if (File.Exists(archivePath) && new FileInfo(archivePath).Length == totalBytes && !File.Exists(mapPath)) {
    return archivePath;
   } else {
    using (var fs = new FileStream(archivePath, FileMode.Create, FileAccess.Write, FileShare.ReadWrite)) {
     fs.SetLength(totalBytes);
    }
    File.WriteAllBytes(mapPath, completed);
   }

   var pending = new ConcurrentQueue<int>();
   long completedBytes = 0;
   for (int i = 0; i < totalChunks; i++) {
    long cLen = Math.Min((long)chunkSize, totalBytes - (long)i * chunkSize);
    if (completed[i] == 1) completedBytes += cLen;
    else pending.Enqueue(i);
   }

   if (pending.IsEmpty) {
    if (File.Exists(mapPath)) File.Delete(mapPath);
    return archivePath;
   }

   object diskLock = new object();
   long sessionBytes = 0;
   Stopwatch sw = Stopwatch.StartNew();
   long lastSampleBytes = 0;
   double lastSampleSec = 0;
   double smoothedMbps = 0;

   using (var uiTimer = new Timer(_ => {
    double nowSec = sw.Elapsed.TotalSeconds;
    long curSession = Interlocked.Read(ref sessionBytes);
    long curTotal = Interlocked.Read(ref completedBytes);
    double dt = nowSec - lastSampleSec;
    if (dt >= 0.45) {
     double instMbps = ((curSession - lastSampleBytes) / (1024.0 * 1024.0)) / dt;
     smoothedMbps = smoothedMbps <= 0.01 ? instMbps : (smoothedMbps * 0.65 + instMbps * 0.35);
     lastSampleBytes = curSession;
     lastSampleSec = nowSec;
    }
    double pct = (curTotal * 100.0) / totalBytes;
    long remain = Math.Max(0, totalBytes - curTotal);
    double etaSec = smoothedMbps > 0.05 ? (remain / (1024.0 * 1024.0)) / smoothedMbps : double.NaN;

    window.Dispatcher.BeginInvoke(new Action(() => {
     if (closed || !busy) return;
     string pctStr = pct.ToString("0.0") + "%  (" + FormatGb(curTotal) + " / " + FormatGb(totalBytes) + ")";
     string spdStr = "⚡ " + smoothedMbps.ToString("0.0") + " MB/s   ·   ETA " + FormatEta(etaSec);
     Text("InstallBadge").Text = "Downloading (" + pct.ToString("0.0") + "%)";
     Text("StatusTitle").Text = "Downloading Aetherfall " + manifest.version + " — " + pct.ToString("0.0") + "%";
     Text("StatusDetail").Text = "Downloaded " + FormatGb(curTotal) + " of " + FormatGb(totalBytes) + "  ·  Safe auto-resume active (you can close or pause anytime)";
     SetProgress(curTotal / (double)totalBytes, pctStr, spdStr);
    }));
   }, null, 100, 250)) {

    int workers = 8;
    var tasks = new List<Task>();
    for (int w = 0; w < workers; w++) {
     tasks.Add(Task.Run(async () => {
      byte[] chunkBuf = new byte[chunkSize];
      int chunkIdx;
      while (!ct.IsCancellationRequested && pending.TryDequeue(out chunkIdx)) {
       long start = (long)chunkIdx * chunkSize;
       long end = Math.Min(totalBytes - 1, start + chunkSize - 1);
       int expectedLen = (int)(end - start + 1);
       bool ok = false;

       for (int attempt = 0; attempt < 6 && !ct.IsCancellationRequested && !ok; attempt++) {
        int readInAttempt = 0;
        try {
         HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
         req.UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
         req.AddRange(start, end);
         req.Timeout = 30000;
         req.ReadWriteTimeout = 30000;

         using (ct.Register(() => { try { req.Abort(); } catch {} }))
         using (HttpWebResponse resp = (HttpWebResponse)await req.GetResponseAsync()) {
          if (resp.StatusCode != HttpStatusCode.PartialContent && resp.StatusCode != HttpStatusCode.OK)
           throw new IOException("Unexpected HTTP status: " + resp.StatusCode);
          if (resp.ContentType != null && resp.ContentType.IndexOf("text/html", StringComparison.OrdinalIgnoreCase) >= 0)
           throw new IOException("Mirror returned HTML instead of binary range.");

          using (Stream netStream = resp.GetResponseStream()) {
           while (readInAttempt < expectedLen) {
            int r = await netStream.ReadAsync(chunkBuf, readInAttempt, expectedLen - readInAttempt, ct);
            if (r <= 0) break;
            readInAttempt += r;
            Interlocked.Add(ref completedBytes, r);
            Interlocked.Add(ref sessionBytes, r);
           }
          }
         }
         if (readInAttempt != expectedLen)
          throw new IOException("Incomplete chunk read.");

         // Atomic commit: write the verified 8MB block to archive and mark single byte 1 at offset chunkIdx in .partmap
         lock (diskLock) {
          using (FileStream afs = new FileStream(archivePath, FileMode.Open, FileAccess.Write, FileShare.ReadWrite)) {
           afs.Position = start;
           afs.Write(chunkBuf, 0, expectedLen);
           afs.Flush();
          }
          using (FileStream mfs = new FileStream(mapPath, FileMode.Open, FileAccess.Write, FileShare.ReadWrite)) {
           mfs.Position = chunkIdx;
           mfs.WriteByte(1);
           mfs.Flush(true);
          }
          completed[chunkIdx] = 1;
         }
         ok = true;
        } catch (Exception) {
         if (readInAttempt > 0) {
          Interlocked.Add(ref completedBytes, -readInAttempt);
          Interlocked.Add(ref sessionBytes, -readInAttempt);
         }
         if (ct.IsCancellationRequested) throw new OperationCanceledException(ct);
        }
        if (!ok) {
         await Task.Delay(1200 * (attempt + 1), ct);
        }
       }
       if (!ok) {
        pending.Enqueue(chunkIdx);
        throw new IOException("Connection interrupted while downloading chunk " + chunkIdx + ". Click RESUME to continue.");
       }
      }
     }, ct));
    }
    await Task.WhenAll(tasks);
   }

   if (File.Exists(mapPath)) File.Delete(mapPath);
   return archivePath;
  }

  async Task VerifyArchiveSha256Async(string archivePath, string expectedHex, CancellationToken ct) {
   FileInfo fi = new FileInfo(archivePath);
   long total = fi.Length;
   string stampPath = archivePath + ".verified";
   string stampValue = total + ":" + fi.LastWriteTimeUtc.Ticks + ":" + expectedHex.ToLowerInvariant();
   if (File.Exists(stampPath)) {
    try {
     if (File.ReadAllText(stampPath).Trim() == stampValue) return;
    } catch {}
   }

   long processed = 0;
   Stopwatch sw = Stopwatch.StartNew();

   await Task.Run(() => {
    using (var sha = SHA256.Create())
    using (var fs = new FileStream(archivePath, FileMode.Open, FileAccess.Read, FileShare.Read, 4 * 1024 * 1024)) {
     byte[] buf = new byte[4 * 1024 * 1024];
     int read;
     long lastTick = 0;
     while ((read = fs.Read(buf, 0, buf.Length)) > 0) {
      ct.ThrowIfCancellationRequested();
      sha.TransformBlock(buf, 0, read, null, 0);
      processed += read;
      if (sw.ElapsedMilliseconds - lastTick >= 200 || processed == total) {
       lastTick = sw.ElapsedMilliseconds;
       double pct = (processed * 100.0) / total;
       double mbps = (processed / (1024.0 * 1024.0)) / Math.Max(0.1, sw.Elapsed.TotalSeconds);
       long pCopy = processed;
       window.Dispatcher.BeginInvoke(new Action(() => {
        Text("StatusTitle").Text = "Verifying SHA-256 Checksum — " + pct.ToString("0.0") + "%";
        Text("StatusDetail").Text = "Verified " + FormatGb(pCopy) + " of " + FormatGb(total);
        SetProgress(pCopy / (double)total, pct.ToString("0.0") + "%  (" + FormatGb(pCopy) + " / " + FormatGb(total) + ")", "🔍 " + mbps.ToString("0") + " MB/s");
       }));
      }
     }
     sha.TransformFinalBlock(new byte[0], 0, 0);
     string actual = BitConverter.ToString(sha.Hash).Replace("-", "").ToLowerInvariant();
     if (!String.Equals(actual, expectedHex.ToLowerInvariant(), StringComparison.OrdinalIgnoreCase))
      throw new InvalidDataException("SHA-256 mismatch on downloaded archive: expected " + expectedHex + ", got " + actual);
     try { File.WriteAllText(stampPath, stampValue); } catch {}
    }
   }, ct);
  }

  async Task ExtractArchiveWithProgressAsync(string archivePath, string installDir, CancellationToken ct) {
   string sevenZip = System.IO.Path.Combine(root, "bin", "7za.exe");
   if (!File.Exists(sevenZip)) throw new FileNotFoundException("bin\\7za.exe is missing.");
   Directory.CreateDirectory(installDir);
   string marker = System.IO.Path.Combine(installDir, ".aetherfall-extracting");
   File.WriteAllText(marker, DateTime.UtcNow.ToString("O"));

   var psi = new ProcessStartInfo(sevenZip, "x " + Quote(archivePath) + " -o" + Quote(installDir) + " -y -aoa -bsp1") {
    WorkingDirectory = root,
    UseShellExecute = false,
    CreateNoWindow = true,
    WindowStyle = ProcessWindowStyle.Hidden,
    RedirectStandardOutput = true,
    RedirectStandardError = true
   };

   Stopwatch sw = Stopwatch.StartNew();
   using (var proc = new Process { StartInfo = psi }) {
    using (ct.Register(() => { try { if (!proc.HasExited) proc.Kill(); } catch {} })) {
     proc.OutputDataReceived += (s, e) => {
      if (String.IsNullOrWhiteSpace(e.Data)) return;
      Match m = Regex.Match(e.Data, @"(\d{1,3})%");
      if (m.Success) {
       int p;
       if (int.TryParse(m.Groups[1].Value, out p)) {
        window.Dispatcher.BeginInvoke(new Action(() => {
         Text("StatusTitle").Text = "Extracting Aion Aetherfall — " + p + "%";
         Text("StatusDetail").Text = "Unpacking 197,739 files into " + installDir;
         SetProgress(p / 100.0, p + "% Extracted", "📦 Extracting (" + (int)sw.Elapsed.TotalSeconds + "s)");
        }));
       }
      }
     };
     proc.Start();
     proc.BeginOutputReadLine();
     proc.BeginErrorReadLine();
     await Task.Run(() => proc.WaitForExit());
     if (ct.IsCancellationRequested) throw new OperationCanceledException(ct);
     if (proc.ExitCode != 0) throw new InvalidOperationException("7-Zip extraction stopped with exit code " + proc.ExitCode);
    }
   }
   if (File.Exists(marker)) File.Delete(marker);
  }

  async Task RunInstall() {
   SaveSettingsFromUi();
   string pkg = ResolveInstallPath();
   string dlDir = ResolveDownloadDir();
   ReleaseFile rf = (manifest.files != null && manifest.files.Count > 0) ? manifest.files[0] : new ReleaseFile {
    name = "Aetherfall-0.86.7z",
    bytes = 39030688343L,
    sha256 = "7b5599837887a49d2b2bebb2ef73f4a9435b42b9686da62aec6354f9954af885"
   };

   downloadCts = new CancellationTokenSource();
   long partDone, partTotal; double partPct;
   if (GetPartialDownloadProgress(out partDone, out partTotal, out partPct)) {
    SetBusy(true, "RESUMING AETHERFALL", "Resuming from " + partPct.ToString("0.0") + "%…", "Reconnecting to mirror at " + FormatGb(partDone) + " / " + FormatGb(partTotal) + "…", true);
    SetProgress(partDone / (double)partTotal, partPct.ToString("0.0") + "%  (" + FormatGb(partDone) + " / " + FormatGb(partTotal) + ")", "Reconnecting…");
   } else {
    SetBusy(true, "INSTALLING AETHERFALL", "Preparing download…", "Connecting to mirror…", true);
    SetProgress(0.01, "0.0%  (0.00 GB / " + FormatGb(rf.bytes) + ")", "Connecting…");
   }

   try {
    string archivePath = FindCompleteLocalArchive();
    if (archivePath == null) {
     MirrorConfig mirrors = await Task.Run(() => LoadMirrors());
     string httpUrl = null;
     if (mirrors.Files != null && mirrors.Files.ContainsKey(rf.name) && mirrors.Files[rf.name] != null && mirrors.Files[rf.name].Count > 0)
      httpUrl = mirrors.Files[rf.name][0];

     if (settings.Mode != "Torrent" && !String.IsNullOrWhiteSpace(httpUrl)) {
      archivePath = await DownloadArchiveBoundedRangesAsync(rf, httpUrl, dlDir, downloadCts.Token);
     } else {
      string script = System.IO.Path.Combine(root, "bin", "Download.ps1");
      int code = await RunPowerShell(script, "-Mode Torrent -Destination " + Quote(System.IO.Path.GetDirectoryName(pkg)), line => {
       if (!String.IsNullOrWhiteSpace(line)) Text("StatusDetail").Text = line;
      });
      if (code != 0) throw new InvalidOperationException(Text("StatusDetail").Text);
      archivePath = System.IO.Path.Combine(dlDir, rf.name);
     }
    }

    if (!IsInstalled()) {
     await VerifyArchiveSha256Async(archivePath, rf.sha256, downloadCts.Token);
     await ExtractArchiveWithProgressAsync(archivePath, pkg, downloadCts.Token);
    }

    try {
     string selfExe = Process.GetCurrentProcess().MainModule.FileName;
     File.Copy(selfExe, System.IO.Path.Combine(pkg, "Aetherfall Hub.exe"), true);
     string updScript = System.IO.Path.Combine(root, "bin", "Update-Repack.ps1");
     if (File.Exists(updScript)) File.Copy(updScript, System.IO.Path.Combine(pkg, "Control", "Update-Repack.ps1"), true);
    } catch {}

    if (Find<CheckBox>("DeleteArchiveAfter").IsChecked == true && archivePath != null && File.Exists(archivePath)) {
     try { File.Delete(archivePath); } catch {}
    }

    SetProgress(1.0, "100% Complete", "✅ Ready to Play");
    SetBusy(false, "INSTALLATION COMPLETE", "Aetherfall is ready!", "Click PLAY to open the Aetherfall Launcher, or use Server Control to start MariaDB & GameServer.");
    RefreshState();
   } catch (OperationCanceledException) {
    SetBusy(false, "DOWNLOAD PAUSED", "Download paused — Progress saved.", "Click RESUME anytime to continue from the exact chunk where you left off.");
    RefreshState();
   } catch (Exception ex) {
    SetBusy(false, "ACTION NEEDED", "Installation paused", ex.Message);
    RefreshState();
   } finally {
    downloadCts = null;
   }
  }

  async Task RunUpdate(string chosenZip) {
   string pkg = ResolveInstallPath();
   if (!File.Exists(System.IO.Path.Combine(pkg, "REPACK.json"))) {
    MessageBox.Show("Install the repack first before applying an update patch.", "Aion Aetherfall", MessageBoxButton.OK, MessageBoxImage.Information);
    return;
   }
   string updScript = System.IO.Path.Combine(pkg, "Control", "Update-Repack.ps1");
   if (!File.Exists(updScript)) updScript = System.IO.Path.Combine(root, "bin", "Update-Repack.ps1");
   if (!File.Exists(updScript)) updScript = System.IO.Path.Combine(root, "Update-Repack.ps1");
   if (!File.Exists(updScript)) {
    MessageBox.Show("Update-Repack.ps1 was not found.", "Aion Aetherfall", MessageBoxButton.OK, MessageBoxImage.Error);
    return;
   }

   if (String.IsNullOrEmpty(chosenZip) && String.IsNullOrWhiteSpace(settings.UpdateUrl)) {
    string ver = InstalledVersion();
    string userDl = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
    foreach (string folder in new[] { pkg, System.IO.Path.Combine(pkg, "Updates"), root, userDl }) {
     if (!Directory.Exists(folder)) continue;
     var candidates = Directory.GetFiles(folder, "Aetherfall-Patch-*.zip").OrderBy(x => x).ToList();
     var newer = candidates.LastOrDefault(x => !System.IO.Path.GetFileName(x).Equals("Aetherfall-Patch-" + ver + ".zip", StringComparison.OrdinalIgnoreCase));
     if (newer != null) { chosenZip = newer; break; }
    }
    if (String.IsNullOrEmpty(chosenZip)) {
     var dlg = new OpenFileDialog {
      Title = "Select Aetherfall-Patch-<version>.zip to update your repack",
      Filter = "Aetherfall Patch ZIP (Aetherfall-Patch-*.zip;*.zip)|Aetherfall-Patch-*.zip;*.zip"
     };
     if (dlg.ShowDialog() != true) return;
     chosenZip = dlg.FileName;
    }
   }

   SetBusy(true, "UPDATING REPACK", "Applying delta update…", "Verifying patch checksums and backing up replaced files…");
   SetProgress(0.45, "Updating…", "⚡ Delta Patch");
   try {
    string args = "-PackagePath " + Quote(pkg);
    if (!String.IsNullOrEmpty(chosenZip)) args += " -PatchFile " + Quote(chosenZip);
    if (!String.IsNullOrWhiteSpace(settings.UpdateUrl)) args += " -UpdateUrl " + Quote(settings.UpdateUrl);
    string lastLine = "";
    int code = await RunPowerShell(updScript, args, line => {
     if (!String.IsNullOrWhiteSpace(line)) { lastLine = line; Text("StatusDetail").Text = line; }
    });
    if (code != 0) throw new InvalidOperationException(lastLine.Length > 0 ? lastLine : "Update stopped.");
    SetProgress(1.0, "100%", "✅ Up to date");
    SetBusy(false, "UPDATE COMPLETE", "Repack is up to date", lastLine);
    RefreshState();
   } catch (Exception ex) {
    SetBusy(false, "UPDATE PAUSED", "Could not complete update", ex.Message);
   }
  }

  async Task RunServerControl(string action) {
   string pkg = ResolveInstallPath();
   if (!File.Exists(System.IO.Path.Combine(pkg, "REPACK.json"))) {
    Text("ServerControlFeedback").Text = "Install the repack first.";
    return;
   }
   try {
    Text("ServerControlFeedback").Text = "Running " + action + "…";
    if (action == "StartAll" || action == "StartDb") {
     await RunPowerShell(System.IO.Path.Combine(pkg, "Control", "Start-Database.ps1"), "", line => {
      if (!String.IsNullOrWhiteSpace(line)) Text("ServerControlFeedback").Text = line;
     });
    }
    if (action == "StartAll" || action == "StartGs") {
     await RunPowerShell(System.IO.Path.Combine(pkg, "Control", "Start-Servers.ps1"), "", line => {
      if (!String.IsNullOrWhiteSpace(line)) Text("ServerControlFeedback").Text = line;
     });
    }
    if (action == "StopDb") {
     await RunPowerShell(System.IO.Path.Combine(pkg, "Control", "Stop-Database.ps1"), "", line => {
      if (!String.IsNullOrWhiteSpace(line)) Text("ServerControlFeedback").Text = line;
     });
    }
    await PollPorts();
   } catch (Exception ex) {
    Text("ServerControlFeedback").Text = ex.Message;
   }
  }

  async Task<int> RunPowerShell(string scriptPath, string extraArgs, Action<string> onLine) {
   string ps = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
   string fullArgs = "-NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File " + Quote(scriptPath) + " " + extraArgs;
   var psi = new ProcessStartInfo(ps, fullArgs) {
    WorkingDirectory = System.IO.Path.GetDirectoryName(scriptPath),
    UseShellExecute = false,
    CreateNoWindow = true,
    WindowStyle = ProcessWindowStyle.Hidden,
    RedirectStandardOutput = true,
    RedirectStandardError = true
   };
   using (var proc = new Process { StartInfo = psi }) {
    proc.OutputDataReceived += (s, e) => { if (e.Data != null) window.Dispatcher.BeginInvoke(new Action(() => onLine(e.Data))); };
    proc.ErrorDataReceived += (s, e) => { if (e.Data != null) window.Dispatcher.BeginInvoke(new Action(() => onLine(e.Data))); };
    proc.Start();
    proc.BeginOutputReadLine();
    proc.BeginErrorReadLine();
    await Task.Run(() => proc.WaitForExit());
    return proc.ExitCode;
   }
  }

  async Task PollPorts() {
   if (closed) return;
   bool dbUp = false, gsUp = false;
   await Task.Run(() => {
    try {
     var listeners = IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners();
     dbUp = listeners.Any(p => p.Port == 33306);
     gsUp = listeners.Any(p => p.Port == 2106);
    } catch {}
   });
   if (closed) return;
   Find<Ellipse>("DbDot").Fill = Brush(dbUp ? "#9CDEC3" : "#7E8B9B");
   Text("DbBadge").Text = dbUp ? "Database ready (33306)" : "Database stopped";
   Find<Ellipse>("GsDot").Fill = Brush(gsUp ? "#9CDEC3" : "#7E8B9B");
   Text("GsBadge").Text = gsUp ? "LoginServer online (2106)" : "Servers stopped";
  }

  void AddParticles() {
   Canvas canvas = Find<Canvas>("Motes");
   for (int i = 0; i < 30; i++) {
    double size = random.NextDouble() * 2.2 + 1;
    Ellipse mote = new Ellipse { Width = size, Height = size, Fill = Brush(i % 2 == 0 ? "#8AB4F8" : "#F0D7A1"), Opacity = random.NextDouble() * .4 + .12, IsHitTestVisible = false };
    Canvas.SetLeft(mote, random.NextDouble() * 1120);
    Canvas.SetTop(mote, 80 + random.NextDouble() * 580);
    particles.Add(mote);
    canvas.Children.Add(mote);
   }
  }

  void StartGlow() {
   var glow = Find<System.Windows.Media.Effects.DropShadowEffect>("PrimaryGlow");
   if (glow == null) return;
   var pulse = new DoubleAnimation(.20, .52, TimeSpan.FromSeconds(2.6)) { AutoReverse = true, RepeatBehavior = RepeatBehavior.Forever };
   glow.BeginAnimation(System.Windows.Media.Effects.DropShadowEffect.OpacityProperty, pulse);
  }

  void Animate() {
   if (window.WindowState == WindowState.Minimized) return;
   for (int i = 0; i < particles.Count; i++) {
    Ellipse mote = particles[i];
    double y = Canvas.GetTop(mote) - (.10 + i % 4 * .035), x = Canvas.GetLeft(mote) + Math.Sin(ticks * .009 + i) * .06;
    if (y < 30) y = 650;
    Canvas.SetTop(mote, y); Canvas.SetLeft(mote, x);
   }
   if (busy) {
    Find<Rectangle>("ProgressShine").Opacity = 1;
    ((TranslateTransform)Find<Rectangle>("ProgressShine").RenderTransform).X = (ticks * 6 % 700) - 90;
   } else {
    Find<Rectangle>("ProgressShine").Opacity = 0;
   }
  }

  static void OpenFolder(string path) {
   try {
    if (!Directory.Exists(path)) Directory.CreateDirectory(path);
    Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
   } catch (Exception ex) { MessageBox.Show(ex.Message, "Aion Aetherfall"); }
  }

  void RenderPreview(string[] args) {
   int at = Array.IndexOf(args, "--preview");
   string dest = (at >= 0 && at + 1 < args.Length) ? args[at + 1] : "setup-preview.png";
   Text("InstallPathLabel").Text = @"D:\Games\Aetherfall-0.86";
   Text("HeroHeadline").Text = "Install & Manage Aetherfall.";
   Find<Ellipse>("InstallDot").Fill = Brush("#E0B580");
   Text("InstallBadge").Text = "Downloading (38.4%)";
   Text("PrimaryLabel").Text = "PAUSE";
   Text("LaunchCaption").Text = "DOWNLOADING & VERIFYING (v0.86)";
   scene.Measure(new Size(1180, 744));
   scene.Arrange(new Rect(0, 0, 1180, 744));
   scene.UpdateLayout();
   SetProgress(0.384, "38.4%  (13.96 GB / 36.35 GB)", "⚡ 42.8 MB/s   ·   ETA 08m 45s");
   Text("StatusTitle").Text = "Downloading Aetherfall 0.86 — 38.4%";
   Text("StatusDetail").Text = "Downloaded 13.96 GB of 36.35 GB  ·  8 parallel range streams (Resume enabled)";
   scene.UpdateLayout();
   var bmp = new RenderTargetBitmap(1180, 744, 96, 96, PixelFormats.Pbgra32);
   bmp.Render(scene);
   var enc = new PngBitmapEncoder();
   enc.Frames.Add(BitmapFrame.Create(bmp));
   using (var fs = File.Create(dest)) enc.Save(fs);
  }
 }
}
