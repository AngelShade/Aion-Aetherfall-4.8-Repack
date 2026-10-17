using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Management;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Threading;

namespace AetherfallSetup {
 // Normal console CTRL+C invokes HotSpot's shutdown hooks. CTRL+BREAK only
 // dumps threads, and closing a console can cut saving short. Never kill Java.
 internal static class ServerControl {
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool AttachConsole(uint processId);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool FreeConsole();
  [DllImport("kernel32.dll", SetLastError=true)] static extern uint GetConsoleProcessList([Out] uint[] ids, uint count);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool SetConsoleCtrlHandler(IntPtr handler, bool add);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool GenerateConsoleCtrlEvent(uint signal, uint group);
  [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr GetStdHandle(int kind);
  [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
  static extern bool WriteConsoleInputW(IntPtr input, InputRecord[] records, uint count, out uint written);
  [StructLayout(LayoutKind.Explicit, Size=20)] struct InputRecord {
   [FieldOffset(0)] public ushort Type;
   [FieldOffset(4)] public int Down;
   [FieldOffset(8)] public ushort Repeat;
   [FieldOffset(10)] public ushort Key;
   [FieldOffset(14)] public char Character;
  }
  sealed class Actor {
   public int Id, Parent;
   public string Name, Executable, Command;
  }
  static List<Actor> Processes() {
   var result = new List<Actor>();
   using (var query = new ManagementObjectSearcher("SELECT ProcessId,ParentProcessId,Name,ExecutablePath,CommandLine FROM Win32_Process"))
   using (var rows = query.Get()) {
    foreach (ManagementObject row in rows) using (row) {
     result.Add(new Actor { Id=Convert.ToInt32(row["ProcessId"]), Parent=Convert.ToInt32(row["ParentProcessId"]),
      Name=Convert.ToString(row["Name"]), Executable=Convert.ToString(row["ExecutablePath"]), Command=Convert.ToString(row["CommandLine"]) });
    }
   }
   return result;
  }
  static bool Matches(Actor p, string javaRoot, string main) {
   return (p.Name.Equals("java.exe",StringComparison.OrdinalIgnoreCase) || p.Name.Equals("javaw.exe",StringComparison.OrdinalIgnoreCase))
    && !String.IsNullOrEmpty(p.Executable) && Path.GetFullPath(p.Executable).StartsWith(javaRoot,StringComparison.OrdinalIgnoreCase)
    && Regex.IsMatch(p.Command,@"(?:^|\s)"+Regex.Escape(main)+@"(?:\s|$)");
  }
  static HashSet<int> ConsoleIds() {
   var ids = new uint[64]; uint count = GetConsoleProcessList(ids,(uint)ids.Length);
   if (count==0 || count>ids.Length) throw new InvalidOperationException("Cannot verify this server's console. Close it normally from its window.");
   return new HashSet<int>(ids.Take((int)count).Select(x=>(int)x));
  }
  static bool Exited(Process process) { try { return process.HasExited; } catch (InvalidOperationException) { return true; } }
  static void ConsoleAnswer(string answer) {
   var keys = new List<InputRecord>();
   foreach (char c in answer) {
    keys.Add(new InputRecord { Type=1, Down=1, Repeat=1, Key=(ushort)(c=='\r'?13:Char.ToUpperInvariant(c)), Character=c });
    keys.Add(new InputRecord { Type=1, Down=0, Repeat=1, Key=(ushort)(c=='\r'?13:Char.ToUpperInvariant(c)), Character=c });
   }
   uint written;
   if (!WriteConsoleInputW(GetStdHandle(-10),keys.ToArray(),(uint)keys.Count,out written) || written!=keys.Count)
    throw new Win32Exception(Marshal.GetLastWin32Error(),"The server stopped, but its console confirmation could not be completed.");
  }
  public static string Stop(string package, string component, Action<string> progress) {
   string main;
   switch (component) {
    case "GameServer": main="com.aionemu.gameserver.GameServer"; break;
    case "LoginServer": main="com.aionemu.loginserver.LoginServer"; break;
    case "ChatServer": main="com.aionemu.chatserver.ChatServer"; break;
    default: throw new ArgumentException("Unknown server component.");
   }
   string javaRoot=Path.GetFullPath(Path.Combine(package,"Server","runtime","java"))+Path.DirectorySeparatorChar;
   var processes=Processes(); var targets=processes.Where(p=>Matches(p,javaRoot,main)).ToList();
   if(targets.Count==0)return component+" is already stopped.";
   foreach(var target in targets) {
    if(Regex.IsMatch(target.Command,@"(?:^|\s)-Xrs(?:\s|$)"))
     throw new InvalidOperationException(component+" disables normal console shutdown. Stop it from its server console.");
    using(var server=Process.GetProcessById(target.Id)) {
     // Pin this process handle before signalling, so PID reuse cannot satisfy
     // an exit check for another process. Revalidate the selected executable.
     IntPtr pinned=server.Handle;
     if(Exited(server))continue;
     if(!String.Equals(server.MainModule.FileName,target.Executable,StringComparison.OrdinalIgnoreCase))
      throw new InvalidOperationException("The selected server changed; refresh and try again.");
     var parents=new HashSet<int>();int parent=target.Parent;
     for(int i=0;i<4;i++) {
      var shell=processes.FirstOrDefault(p=>p.Id==parent);
      if(shell==null || !shell.Name.Equals("cmd.exe",StringComparison.OrdinalIgnoreCase))break;
      parents.Add(shell.Id);parent=shell.Parent;
     }
     var shells=new List<Process>();bool attached=false;
     try {
      if(!AttachConsole((uint)target.Id))throw new Win32Exception(Marshal.GetLastWin32Error(),"Cannot access this server's console. Stop it from its window.");
      attached=true;
      if(!SetConsoleCtrlHandler(IntPtr.Zero,true))throw new Win32Exception(Marshal.GetLastWin32Error());
      int self=Process.GetCurrentProcess().Id;var ids=ConsoleIds();
      if(!ids.Contains(target.Id) || ids.Any(id=>id!=self && id!=target.Id && !parents.Contains(id)))
       throw new InvalidOperationException("This console is shared with another program. Stop the selected server manually to leave that program alone.");
      foreach(int id in ids.Where(parents.Contains)) {
       try { var shell=Process.GetProcessById(id);IntPtr handle=shell.Handle;shells.Add(shell); } catch(ArgumentException) { }
      }
      progress("Stopping "+component+" normally; waiting for saving and shutdown…");
      if(!GenerateConsoleCtrlEvent(0,0))throw new Win32Exception(Marshal.GetLastWin32Error());
      if(!server.WaitForExit(180000))
       throw new InvalidOperationException(component+" is still shutting down. No process was forcibly stopped; let saving finish and check its console.");
      // Only after Java has completely exited may we answer cmd's Y/N or
      // PAUSE prompts. No foreground keystrokes, other windows or terminals.
      for(int attempt=0;attempt<8 && shells.Any(p=>!Exited(p));attempt++) {
       ids=ConsoleIds();
       if(ids.Any(id=>id!=self && !parents.Contains(id)))
        throw new InvalidOperationException(component+" stopped; its console is now used by another program and was left open.");
       ConsoleAnswer(attempt==0?"Y\r":"\r");Thread.Sleep(350);
      }
      if(shells.Any(p=>!Exited(p)))throw new InvalidOperationException(component+" stopped, but its launcher console is still open. Close that window normally.");
     } finally {
      if(attached) { FreeConsole();SetConsoleCtrlHandler(IntPtr.Zero,false); }
      foreach(var shell in shells)shell.Dispose();
     }
    }
   }
   if(Processes().Any(p=>Matches(p,javaRoot,main)))throw new InvalidOperationException(component+" is still running or restarted; check its console.");
   return component+" stopped; its server and launcher processes have exited.";
  }
 }
}
