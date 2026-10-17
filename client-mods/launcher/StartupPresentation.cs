using System;
using System.Runtime.InteropServices;
using System.Text;

namespace Aetherfall {
 // Read-only discovery includes hidden windows. Presentation belongs entirely
 // to the client, starting before its native splash routine can create a HWND.
 internal static class ClientWindow {
  delegate bool EnumCallback(IntPtr window,IntPtr ignored);
  [DllImport("user32.dll")] static extern bool EnumWindows(EnumCallback callback,IntPtr parameter);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window,out uint pid);
  [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr window,StringBuilder name,int count);
  [DllImport("user32.dll")] internal static extern bool IsWindowVisible(IntPtr window);
  internal static IntPtr Find(int processId) {
   IntPtr found=IntPtr.Zero;
   EnumWindows(delegate(IntPtr window,IntPtr ignored) {
    uint pid;GetWindowThreadProcessId(window,out pid);if(pid!=(uint)processId)return true;
    StringBuilder name=new StringBuilder(64);GetClassName(window,name,64);
    if(name.ToString()!="AIONClientWndClass1.0")return true;
    found=window;return false;
   },IntPtr.Zero);
   return found;
  }
 }
}
