using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;

namespace AetherfallSetup {
 public sealed partial class SetupApp {
  const string VerifyLabel="✓  Verify Files";
  CancellationTokenSource verifyCancel;
  List<string> repairFiles;
  void InitializeVerify() {
   Btn("VerifyFilesBtn").Click+=async delegate {
    if(verifyCancel!=null)verifyCancel.Cancel();
    else if(repairFiles!=null)await RepairFiles();
    else await VerifyFiles(null);
   };
  }
  void ResetRepair() { repairFiles=null;Btn("VerifyFilesBtn").Content=VerifyLabel; }
  async Task<RepackVerificationResult> CheckFiles(string pkg,ICollection<string> only) {
   Btn("VerifyFilesBtn").Content="■  Stop";Btn("VerifyFilesBtn").IsEnabled=true;
   verifyCancel=new CancellationTokenSource();var token=verifyCancel.Token;
   var clock=Stopwatch.StartNew();long shown=0;
   Action<long,long> progress=(done,total)=>{
    if(clock.ElapsedMilliseconds-Interlocked.Read(ref shown)<250&&done<total)return;
    Interlocked.Exchange(ref shown,clock.ElapsedMilliseconds);
    window.Dispatcher.BeginInvoke(new Action(()=>{
     if(closed)return;
     double rate=done/Math.Max(.001,clock.Elapsed.TotalSeconds);
     SetProgress(total>0?(double)done/total:1,(total>0?100.0*done/total:100).ToString("0.0")+"%",FormatUpdateBytes((long)rate)+"/s  ·  ETA "+(rate>0?FormatEta((total-done)/rate):"calculating…"));
     Text("StatusDetail").Text=FormatUpdateBytes(done)+" / "+FormatUpdateBytes(total)+" checked. Your settings, characters and languages are not touched.";
    }));
   };
   try { return await Task.Run(()=>RepackVerification.Run(pkg,only,progress,token)); }
   finally { verifyCancel.Dispose();verifyCancel=null;Btn("VerifyFilesBtn").Content=VerifyLabel; }
  }
  static string Sample(IEnumerable<string> files) {
   var list=files.ToList();return String.Join(", ",list.Take(3).Select(x=>Path.GetFileName(x)))+(list.Count>3?" and "+(list.Count-3)+" more":"");
  }
  async Task VerifyFiles(ICollection<string> only) {
   if(busy||serverControlBusy||modControlBusy)return;
   string pkg=ResolveInstallPath();
   ResetRepair();
   if(!IsInstalled()) { SetBusy(false,"FULL BASE REQUIRED","Install or select the full repack first",RepackInstallation.Problem(pkg));return; }
   SetBusy(true,"VERIFYING FILES",only==null?"Checking your game files…":"Confirming the repaired files…","Reading the installed file list…");
   SetProgress(0,"Checking…","");
   try {
    var result=await CheckFiles(pkg,only);
    string log=Path.Combine(pkg,"Control","Logs","verify-latest.log");Directory.CreateDirectory(Path.GetDirectoryName(log));
    File.WriteAllLines(log,new[]{DateTime.Now.ToString("s")+" Checked "+result.Checked+" files; "+result.Problems+" problems"}.Concat(result.Missing.Select(x=>"MISSING "+x)).Concat(result.Changed.Select(x=>"CHANGED "+x)));
    SetProgress(result.Problems==0?1:0,result.Problems==0?"100%":"",result.Problems==0?"✅ Verified":"");
    var repairable=result.Repairable.ToList();
    if(result.Problems==0)SetBusy(false,only==null?"FILES VERIFIED":"FILES REPAIRED",only==null?"All game files are correct":"Your game files are repaired",result.Checked.ToString("N0")+" files checked. Your settings were not touched.");
    else if(only!=null)SetBusy(false,"REPAIR INCOMPLETE",result.Problems+" files could not be repaired",Sample(result.Missing.Concat(result.Changed))+" are not in the update. Reinstall the full repack to replace them. List: Control\\Logs\\verify-latest.log");
    else if(repairable.Count==0)SetBusy(false,"HD TEXTURES NEED ATTENTION",result.Problems+" HD texture files need reinstalling","Reinstall the HD pack from Mods & Tools. List: Control\\Logs\\verify-latest.log");
    else {
     SetBusy(false,"PROBLEMS FOUND",result.Problems+" damaged or missing files found",Sample(result.Missing.Concat(result.Changed))+". Click Repair to replace only these files. List: Control\\Logs\\verify-latest.log");
     repairFiles=repairable;Btn("VerifyFilesBtn").Content="⚠  Repair "+repairable.Count+(repairable.Count==1?" File":" Files");
    }
   } catch(OperationCanceledException) { SetProgress(0,"","");SetBusy(false,"VERIFICATION STOPPED","File check stopped","Nothing was changed. Verify Files starts a new check.");
   } catch(Exception error) { SetProgress(0,"","");SetBusy(false,"VERIFICATION PAUSED","Could not check your files",error.Message); }
   RefreshState(true);
  }
  async Task RepairFiles() {
   if(busy||serverControlBusy||modControlBusy)return;
   var files=repairFiles;ResetRepair();
   string list=Path.Combine(ResolveInstallPath(),"Control","Logs","verify-repair.txt");
   Directory.CreateDirectory(Path.GetDirectoryName(list));File.WriteAllLines(list,files);
   if(await RunUpdate(null,list))await VerifyFiles(files);
  }
 }
}
