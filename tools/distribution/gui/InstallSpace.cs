using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace AetherfallSetup {
 sealed class VolumeFacts { public string Volume, Format; public long Free; }
 sealed class SpacePlan { public string Problem; public bool MoveDownloads; }

 // Decides before a base install whether the archive and the game both fit.
 // The archive is saved beside Setup by default, which can be a different and
 // much fuller drive than the install folder the player chose.
 static class InstallSpace {
  const long Margin = 2L * 1024 * 1024 * 1024;

  [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
  static extern bool GetDiskFreeSpaceEx(string directory, out ulong available, out ulong total, out ulong free);
  [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
  static extern bool GetVolumePathName(string path, StringBuilder volume, int length);

  // Null means unknown; an unreadable drive is never a reason to refuse an install.
  public static VolumeFacts Read(string path) {
   try {
    string existing = Path.GetFullPath(path);
    while (existing != null && !Directory.Exists(existing)) existing = Path.GetDirectoryName(existing);
    if (existing == null) return null;
    ulong available, total, free;
    if (!GetDiskFreeSpaceEx(existing, out available, out total, out free)) return null;
    var volume = new StringBuilder(1024);
    if (!GetVolumePathName(existing, volume, volume.Capacity)) return null;
    string format = "";
    if (volume.Length == 3) { try { format = new DriveInfo(volume.ToString()).DriveFormat; } catch {} }
    return new VolumeFacts { Volume = volume.ToString(), Format = format, Free = (long)Math.Min(available, (ulong)long.MaxValue) };
   } catch { return null; }
  }

  public static SpacePlan Plan(VolumeFacts download, VolumeFacts install, long missingArchive, bool archiveStarted, long installedBytes) {
   if (download == null || install == null) return new SpacePlan();
   if (IsFat32(install)) return new SpacePlan { Problem = Fat32(install) };
   bool sameVolume = String.Equals(download.Volume, install.Volume, StringComparison.OrdinalIgnoreCase);
   long installNeed = installedBytes + Margin;
   long bothNeed = missingArchive + installedBytes + Margin;
   if (sameVolume) return install.Free >= bothNeed ? new SpacePlan() : new SpacePlan { Problem = Short(install, bothNeed) };
   if (install.Free < installNeed) return new SpacePlan { Problem = Short(install, installNeed) };
   if (missingArchive <= 0) return new SpacePlan();
   bool downloadFits = !IsFat32(download) && download.Free >= missingArchive + Margin;
   if (downloadFits) return new SpacePlan();
   if (!archiveStarted && install.Free >= bothNeed) return new SpacePlan { MoveDownloads = true };
   return new SpacePlan { Problem = IsFat32(download) ? Fat32(download) : Short(download, missingArchive + Margin) };
  }

  static bool IsFat32(VolumeFacts volume) { return String.Equals(volume.Format, "FAT32", StringComparison.OrdinalIgnoreCase); }
  static string Fat32(VolumeFacts volume) { return volume.Volume + " uses FAT32, which cannot hold the game files. Use Change Folder to choose an NTFS or exFAT drive."; }
  static string Short(VolumeFacts volume, long need) {
   return volume.Volume + " has " + Gib(volume.Free) + " GiB free, but this install needs " + Gib(need) + " GiB there. Use Change Folder to choose a drive with more room; the download goes there too.";
  }
  static string Gib(long bytes) { return (bytes / 1073741824.0).ToString("0.0"); }
 }
}
