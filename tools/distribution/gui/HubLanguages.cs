using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;
using Aetherfall;
using AetherfallUpdate;

namespace AetherfallSetup {
 public sealed partial class SetupApp {
  LanguageSelector languageSelector;
  string languageRoot;
  CancellationTokenSource languageCancel;
  static string LanguageSize(long bytes) { return bytes>=1073741824?(bytes/1073741824.0).ToString("0.00")+" GiB":(bytes/1048576.0).ToString("0.0")+" MiB"; }
  string LanguageClient { get { return original!=null?original.ClientRoot:Path.Combine(ResolveInstallPath(),"Client"); } }
  void InitializeLanguages() {
   if(original!=null) {
    Btn("LanguagesTab").Visibility=Visibility.Collapsed;
    Find<FrameworkElement>("LanguagesPanel").Visibility=Visibility.Collapsed;
    return;
   }
   Btn("LanguagesTab").Click+=delegate { SwitchTab("Languages"); };
   Btn("LanguageZipBtn").Click+=async delegate {
    if(busy||serverControlBusy||modControlBusy)return;
    var picker=new OpenFileDialog { Title="Select an Aetherfall language pack",Filter="Language packs (*.zip)|*.zip",CheckFileExists=true };
    if(picker.ShowDialog(window)!=true)return;
    try { await InstallLanguage(LanguageDownloads.Identify(LanguageClient,picker.FileName),picker.FileName); }
    catch(Exception error) { Text("LanguageFeedback").Text=error.Message; }
   };
   Btn("PauseLanguageBtn").Click+=delegate { if(languageCancel!=null)languageCancel.Cancel(); };
  }
  void RefreshLanguagePage() {
   if(original!=null)return;
   string client=LanguageClient;
   bool ready=File.Exists(Path.Combine(client,"Aetherfall Launcher.exe"));
   bool enabled=ready&&!busy&&!serverControlBusy&&!modControlBusy;
   Btn("LanguageZipBtn").IsEnabled=enabled;
   if(!ready) {
    Text("LanguageFeedback").Text="Install or select your repack first.";
    Find<ContentControl>("HubLanguageSelector").Content=null;Find<StackPanel>("LanguagePackLibrary").Children.Clear();
    languageSelector=null;languageRoot=null;return;
   }
   try {
    if(languageSelector==null||!String.Equals(languageRoot,client,StringComparison.OrdinalIgnoreCase)) {
     languageSelector=new LanguageSelector(client,true);languageRoot=client;
     Find<ContentControl>("HubLanguageSelector").Content=languageSelector.View;
    } else languageSelector.Refresh(true);
    languageSelector.SetEnabled(enabled);
    var packs=LanguageDownloads.Catalog(client);
    var list=Find<StackPanel>("LanguagePackLibrary");list.Children.Clear();
    foreach(var pack in packs) {
     bool voices=pack.kind=="voice";
     var option=LanguageSelection.Options(client,voices).First(x=>x.Code==pack.code);
     var row=new Grid { Margin=new Thickness(0,0,0,6),Background=Brush("#0E1B29") };
     row.ColumnDefinitions.Add(new ColumnDefinition());row.ColumnDefinitions.Add(new ColumnDefinition { Width=GridLength.Auto });
     var detail=new StackPanel { Margin=new Thickness(8,5,8,5) };
     string name=option.Name.Split('/')[0].Trim();
     detail.Children.Add(new TextBlock { Text=name+(voices?" voices":" text"),Foreground=Brush("#DFE9F0"),FontSize=11,TextWrapping=TextWrapping.Wrap });
     detail.Children.Add(new TextBlock { Text=LanguageSize(pack.bytes),Foreground=Brush("#93ABBC"),FontSize=10,Margin=new Thickness(0,3,0,0) });row.Children.Add(detail);
     FrameworkElement action;
     if(option.Available)action=new TextBlock { Text="Installed",Foreground=Brush("#8FD8B4"),FontSize=10,VerticalAlignment=VerticalAlignment.Center,Margin=new Thickness(0,0,8,0) };
     else {
      bool partial=File.Exists(Path.Combine(client,"Mods","Languages","Cache",pack.sha256+".zip.partial"));
      var button=new Button { Content=partial?"Resume":"Download",Style=(Style)window.FindResource("LanguageFlat"),Margin=new Thickness(0,3,3,3),VerticalAlignment=VerticalAlignment.Center,IsEnabled=enabled,ToolTip="Download this verified pack from Aetherfall's GitHub releases." };
      button.Click+=async delegate { await InstallLanguage(pack,null); };action=button;
     }
     Grid.SetColumn(action,1);row.Children.Add(action);list.Children.Add(row);
    }
   } catch(Exception error) { Text("LanguageFeedback").Text=error.Message; }
  }
  void LanguageTransferProgress(UpdateProgress progress) {
   if(closed)return;
   window.Dispatcher.BeginInvoke(new Action(()=> {
    if(closed||languageCancel==null)return;
    double pct=progress.total>0?100.0*progress.completed/progress.total:0;
    Find<ProgressBar>("HubLanguageProgress").Value=Math.Max(0,Math.Min(100,pct));
    string phase=progress.phase=="download"?"Downloading":progress.phase=="checking"?"Checking":progress.phase=="verifying"?"Verifying":progress.phase=="installing"?"Installing":"Preparing";
    string detail=phase+" · "+pct.ToString("0.0")+"%";
    if(progress.unit=="bytes")detail+=" · "+LanguageSize(Math.Max(0,progress.total-progress.completed))+" remaining";
    if(progress.bytesPerSecond>0)detail+=" · "+LanguageSize((long)progress.bytesPerSecond)+"/s · ETA "+TimeSpan.FromSeconds(Math.Max(0,(progress.total-progress.completed)/progress.bytesPerSecond)).ToString(@"hh\:mm\:ss");
    detail+=" · "+progress.item+"/"+progress.items+" "+(progress.unit=="files"?"files":"packages");
    Text("LanguageFeedback").Text=detail;Btn("PauseLanguageBtn").IsEnabled=progress.phase=="download"||progress.phase=="checking";
   }));
  }
  async Task InstallLanguage(LanguageDownload pack,string archive) {
   if(original!=null||busy||serverControlBusy||modControlBusy)return;
   string client=LanguageClient;
   string[] controls={"PrimaryBtn","UpdateNowBtn","ApplyPatchFileBtn","SelectArchiveBtn","BrowseInstall","SavePathsBtn","StartAllServerBtn","StartDbOnlyBtn","StartGsOnlyBtn","StopAllServerBtn","StopGsBtn","StopLsBtn","StopChatBtn"};
   modControlBusy=true;languageCancel=new CancellationTokenSource();foreach(string name in controls)Btn(name).IsEnabled=false;
   RefreshMods();RefreshLanguagePage();Btn("PauseLanguageBtn").Visibility=archive==null?Visibility.Visible:Visibility.Collapsed;
   Find<ProgressBar>("HubLanguageProgress").Visibility=Visibility.Visible;Text("LanguageFeedback").Text="Preparing "+pack.id+"…";
   UpdateTransfer.ProgressChanged+=LanguageTransferProgress;
   try {
    if(archive==null)archive=await Task.Run(()=>LanguageDownloads.Fetch(client,pack,languageCancel.Token));
    languageCancel.Token.ThrowIfCancellationRequested();Btn("PauseLanguageBtn").IsEnabled=false;
    await Task.Run(()=>LanguageDownloads.Install(client,pack,archive));
    Text("LanguageFeedback").Text="Pack installed. Select it and apply when ready; your current choices were preserved.";
   } catch(OperationCanceledException) { Text("LanguageFeedback").Text="Paused. Resume continues from the downloaded bytes."; }
   catch(Exception error) { Text("LanguageFeedback").Text=error.Message; }
   finally {
    UpdateTransfer.ProgressChanged-=LanguageTransferProgress;languageCancel.Dispose();languageCancel=null;modControlBusy=false;
    foreach(string name in controls)Btn(name).IsEnabled=true;Btn("PauseLanguageBtn").Visibility=Visibility.Collapsed;RefreshMods();RefreshLanguagePage();
   }
  }
 }
}
