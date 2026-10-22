using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;

namespace AetherfallSetup {
 public sealed class HdComponent { public string downloadUrl,assetVersion; public long archiveBytes; }
 public sealed class HdSelection { public bool enabled; public string archivePath; }
 public sealed partial class SetupApp {
  bool modControlBusy;
  static T ReadModJson<T>(string path) where T:new() { return File.Exists(path)?new JavaScriptSerializer().Deserialize<T>(File.ReadAllText(path)):new T(); }
  string HdRoot { get { return Path.Combine(ResolveInstallPath(),"Client","Mods","HDTextures"); } }
  void InitializeMods() {
   Btn("ModsTab").Click+=delegate { SwitchTab("Mods"); };
   Btn("HdDownloadBtn").Click+=delegate {
    try {
     var pack=ReadModJson<HdComponent>(Path.Combine(HdRoot,"component.json"));Uri uri;
     if(!Uri.TryCreate(pack.downloadUrl,UriKind.Absolute,out uri) || uri.Scheme!="https" || !String.IsNullOrEmpty(uri.UserInfo))throw new InvalidOperationException("This repack has no valid HD download link. Update the repack first.");
     Process.Start(new ProcessStartInfo(uri.AbsoluteUri){UseShellExecute=true});
     Text("ModsFeedback").Text="Download the HD ZIP on that page, then choose Install ZIP here.";
    } catch(Exception ex) { Text("ModsFeedback").Text=ex.Message; }
   };
   Btn("HdInstallBtn").Click+=async delegate { await ChangeHd(true,true); };
   Find<CheckBox>("HdToggle").Click+=async delegate { await ChangeHd(Find<CheckBox>("HdToggle").IsChecked==true,false); };
   Btn("MakeAdminBtn").Click+=async delegate {
    string name=Find<TextBox>("AdminAccountBox").Text.Trim();
    if(String.IsNullOrEmpty(name)) { Text("ModsFeedback").Text="Enter the account login name first.";return; }
    string hex=BitConverter.ToString(Encoding.UTF8.GetBytes(name)).Replace("-","");
    await RunModTool("Make-Admin.ps1","-AccountNameHex "+hex,"Setting Admin access…");
   };
   RefreshMods();
  }
  void RefreshMods() {
   if(Btn("ModsTab")==null)return;
   Btn("ModsTab").Visibility=original==null?Visibility.Visible:Visibility.Collapsed;
   bool available=original==null && File.Exists(Path.Combine(ResolveInstallPath(),"REPACK.json"));
   bool ready=available && !busy && !serverControlBusy && !modControlBusy;
   bool component=File.Exists(Path.Combine(HdRoot,"component.json"));
   try {
    var state=ReadModJson<HdSelection>(Path.Combine(HdRoot,"installed.json"));
    var pack=ReadModJson<HdComponent>(Path.Combine(HdRoot,"component.json"));
    Find<CheckBox>("HdToggle").IsChecked=state.enabled;
    Text("HdStatus").Text=!available?"Install the repack first.":!component?"Update the repack to manage HD textures.":state.enabled?"HD textures active":"Standard textures active";
    Text("HdPackInfo").Text=component?"Optional HD pack · "+(pack.archiveBytes/1073741824.0).ToString("0.00")+" GiB":"";
    Btn("HdDownloadBtn").IsEnabled=ready && component && !String.IsNullOrWhiteSpace(pack.downloadUrl);
   } catch(Exception ex) {
    Text("HdStatus").Text="Texture information needs attention.";Text("ModsFeedback").Text=ex.Message;
    component=false;Btn("HdDownloadBtn").IsEnabled=false;
   }
   Find<CheckBox>("HdToggle").IsEnabled=ready && component;
   Btn("HdInstallBtn").IsEnabled=ready && component;
   Find<TextBox>("AdminAccountBox").IsEnabled=ready;
   Btn("MakeAdminBtn").IsEnabled=ready && File.Exists(Path.Combine(ResolveInstallPath(),"Control","Make-Admin.ps1"));
  }
  async Task ChangeHd(bool enable,bool pickArchive) {
   if(busy || serverControlBusy || modControlBusy) { RefreshMods();return; }
   string archive=null;
   if(enable) {
    try { archive=ReadModJson<HdSelection>(Path.Combine(HdRoot,"installed.json")).archivePath; }
    catch(Exception ex) { Text("ModsFeedback").Text=ex.Message;RefreshMods();return; }
    if(pickArchive || String.IsNullOrEmpty(archive) || !File.Exists(archive)) {
     var dialog=new OpenFileDialog { Title="Select the downloaded Aetherfall HD texture pack",Filter="HD texture ZIP (*.zip)|*.zip",CheckFileExists=true };
     if(dialog.ShowDialog(window)!=true) { RefreshMods();return; }
     archive=dialog.FileName;
    }
   }
   string arguments="-ClientPath "+Quote(Path.Combine(ResolveInstallPath(),"Client"))+" -Action "+(enable?"Enable":"Disable");
   if(enable)arguments+=" -ArchivePath "+Quote(archive);
   await RunModTool("HDTextures.ps1",arguments,enable?"Verifying and installing HD textures…":"Restoring standard textures…");
  }
  async Task RunModTool(string script,string arguments,string title) {
   if(original!=null || busy || serverControlBusy || modControlBusy)return;
   string package=ResolveInstallPath(),path=Path.Combine(package,"Control",script);
   if(!File.Exists(Path.Combine(package,"REPACK.json")) || !File.Exists(path)) { Text("ModsFeedback").Text="Update the selected repack first.";RefreshMods();return; }
   string[] controls={"PrimaryBtn","UpdateNowBtn","ApplyPatchFileBtn","SelectArchiveBtn","BrowseInstall","StartAllServerBtn","StartDbOnlyBtn","StartGsOnlyBtn","StopAllServerBtn","StopGsBtn","StopLsBtn","StopChatBtn"};
   modControlBusy=true;foreach(string name in controls)Btn(name).IsEnabled=false;RefreshMods();
   Text("ModsFeedback").Text=title;
   try {
    string firstError=null,lastLine=title;
    int code=await RunPowerShell(path,arguments,line=> { if(!String.IsNullOrWhiteSpace(line)){lastLine=line;Text("ModsFeedback").Text=line;} },line=> {if(firstError==null && !String.IsNullOrWhiteSpace(line))firstError=line;});
    if(code!=0)throw new InvalidOperationException(firstError??"The operation did not finish. Review the account or texture settings and retry.");
    Text("ModsFeedback").Text=lastLine;
   } catch(Exception ex) { Text("ModsFeedback").Text=ex.Message; }
   finally { modControlBusy=false;foreach(string name in controls)Btn(name).IsEnabled=true;RefreshMods(); }
  }
 }
}
