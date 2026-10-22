using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace Aetherfall {
 // The Mods window: an overlay over the launcher scene that lists every Mods/<name>/mod.json
 // and saves each switch as the mod's desired state. Files change only before Play.
 public sealed class ModsPage {
  readonly Window window; readonly string root; readonly Func<bool> busy, running;
  List<ModEntry> mods=new List<ModEntry>(); string error;
  static readonly string[] InterfaceStates={"off","on","layout"};
  static readonly Dictionary<string,string> InterfaceNames=new Dictionary<string,string>{{"off","Off"},{"on","Skin"},{"layout","Full HUD"}};
  static readonly Dictionary<string,string> InterfaceHints=new Dictionary<string,string>{
   {"off","The native Aion interface."},{"on","Aetherfall colours on the native windows."},
   {"layout","Skin plus the Aetherfall HUD: unit frames, action bars, square minimap and colour themes."}};
  // Swatches for the interface's colour themes; an unknown theme id shows a neutral swatch.
  static readonly Dictionary<string,string> ThemeSwatches=new Dictionary<string,string>{
   {"aether","#5fb4d0"},{"ember","#c8784a"},{"gold","#c9a464"},{"ice","#c4d2dc"},{"jade","#6cc08a"}};

  public ModsPage(Window window,string root,Func<bool> busy,Func<bool> running) {
   this.window=window; this.root=root; this.busy=busy; this.running=running;
   Find<Button>("ModsClose").Click+=delegate { Close(); };
   Find<Button>("ModsDone").Click+=delegate { Close(); };
   Find<Button>("ModsFolder").Click+=delegate { Launcher.Open(Path.Combine(root,"Mods")); };
   TextBox search=Find<TextBox>("ModsSearch");
   search.TextChanged+=delegate { Find<TextBlock>("ModsSearchHint").Visibility=search.Text.Length==0?Visibility.Visible:Visibility.Hidden; Render(); };
   CheckBox hidden=Find<CheckBox>("ModsShowHidden"); hidden.Checked+=delegate { Render(); }; hidden.Unchecked+=delegate { Render(); };
   Find<Border>("ModsWindow").MouseLeftButtonDown+=delegate(object sender,MouseButtonEventArgs e) { e.Handled=true; };
   Find<FrameworkElement>("ModsOverlay").MouseLeftButtonDown+=delegate { Close(); };
   window.PreviewKeyDown+=delegate(object sender,KeyEventArgs e) { if(IsOpen && e.Key==Key.Escape) { Close(); e.Handled=true; } };
  }
  T Find<T>(string name) where T:class { return window.FindName(name) as T; }
  static SolidColorBrush Brush(string color) { return (SolidColorBrush)new BrushConverter().ConvertFromString(color); }
  public bool IsOpen { get { return Find<FrameworkElement>("ModsOverlay").Visibility==Visibility.Visible; } }

  public void Open() { Refresh(); Find<FrameworkElement>("ModsOverlay").Visibility=Visibility.Visible; Find<ScrollViewer>("ModsScroll").ScrollToTop(); }
  public void Close() { Find<FrameworkElement>("ModsOverlay").Visibility=Visibility.Collapsed; }
  public void Refresh() {
   error=null;
   try { mods=ModCatalog.Read(root); }
   catch(Exception failure) {
    if(!(failure is IOException||failure is UnauthorizedAccessException||failure is InvalidOperationException||failure is ArgumentException))throw;
    mods=new List<ModEntry>(); error="The Mods folder could not be read: "+failure.Message;
   }
   Render();
  }

  static bool Toggles(ModEntry mod) { return mod.Kind==ModKind.Interface||mod.Kind==ModKind.Switchable; }
  void Render() {
   StackPanel list=Find<StackPanel>("ModRows"); list.Children.Clear();
   string query=(Find<TextBox>("ModsSearch").Text??"").Trim();
   bool hidden=Find<CheckBox>("ModsShowHidden").IsChecked==true;
   List<ModEntry> shown=mods.Where(x=>(hidden||x.Info.Listed) && (query.Length==0
    || x.Info.Title.IndexOf(query,StringComparison.OrdinalIgnoreCase)>=0 || x.Name.IndexOf(query,StringComparison.OrdinalIgnoreCase)>=0
    || (x.Info.Description??"").IndexOf(query,StringComparison.OrdinalIgnoreCase)>=0)).ToList();
   Section(list,"SWITCHABLE",shown.Where(Toggles).ToList());
   Section(list,"ALWAYS ON",shown.Where(x=>!Toggles(x)).ToList());
   if(shown.Count==0)list.Children.Add(new TextBlock { Text=mods.Count==0?"No mods with a mod.json were found.":"No mods match your search.",Foreground=Brush("#6f889a"),FontSize=12,Margin=new Thickness(2,18,0,0) });
   int listed=mods.Count(x=>x.Info.Listed);
   Find<TextBlock>("ModsCount").Text=listed+" MODS  ·  "+mods.Count(x=>x.Info.Listed&&Toggles(x))+" SWITCHABLE";
   Note(null);
  }
  void Section(StackPanel list,string title,List<ModEntry> items) {
   if(items.Count==0)return;
   list.Children.Add(new TextBlock { Text=title,Foreground=Brush("#d6b878"),FontSize=10,Margin=new Thickness(2,list.Children.Count==0?6:14,0,5) });
   list.Children.Add(new Border { Height=1,Background=Brush("#8a6d3c"),Margin=new Thickness(0,0,0,8) });
   foreach(ModEntry mod in items)list.Children.Add(Row(mod));
  }

  FrameworkElement Row(ModEntry mod) {
   Border row=new Border { Background=Brush("#0e1b29"),BorderBrush=Brush("#1f3447"),BorderThickness=new Thickness(1),Padding=new Thickness(16,12,16,12),Margin=new Thickness(0,0,0,8) };
   row.MouseEnter+=delegate { row.BorderBrush=Brush("#2e4a61"); }; row.MouseLeave+=delegate { row.BorderBrush=Brush("#1f3447"); };
   Grid grid=new Grid(); grid.ColumnDefinitions.Add(new ColumnDefinition()); grid.ColumnDefinitions.Add(new ColumnDefinition { Width=new GridLength(290) });
   StackPanel text=new StackPanel { Margin=new Thickness(0,0,18,0) };
   StackPanel heading=new StackPanel { Orientation=Orientation.Horizontal };
   heading.Children.Add(new TextBlock { Text=mod.Info.Title,FontFamily=new FontFamily("Georgia"),FontSize=15,Foreground=Brush("#f1dca8") });
   heading.Children.Add(new TextBlock { Text="Mods/"+mod.Name+(mod.Info.Listed?"":"  ·  support"),FontSize=10,Foreground=Brush("#6f889a"),Margin=new Thickness(10,4,0,0) });
   text.Children.Add(heading);
   text.Children.Add(new TextBlock { Text=mod.Info.Description,FontSize=12,Foreground=Brush("#93abbc"),Margin=new Thickness(0,5,0,0),TextWrapping=TextWrapping.Wrap });
   TextBlock status=new TextBlock { FontSize=11,Margin=new Thickness(0,6,0,0),TextWrapping=TextWrapping.Wrap };
   Status(mod,status); text.Children.Add(status);
   if(mod.Missing.Count>0)text.Children.Add(new TextBlock { Text="Missing mod files: "+String.Join(", ",mod.Missing),FontSize=11,Foreground=Brush("#e8a08e"),Margin=new Thickness(0,5,0,0),TextWrapping=TextWrapping.Wrap });
   grid.Children.Add(text);
   FrameworkElement control=Control(mod,status); Grid.SetColumn(control,1); grid.Children.Add(control);
   row.Child=grid; return row;
  }
  void Status(ModEntry mod,TextBlock status) {
   status.Visibility=Visibility.Visible;
   if(mod.Kind==ModKind.Interface||mod.Kind==ModKind.Switchable) {
    Func<string,string> name=x=>mod.Kind==ModKind.Interface?InterfaceNames[x]+(x=="layout"&&mod.Theme!=null?" · "+ThemeName(mod,mod.Theme):""):x=="off"?"Off":"On";
    if(Pending(mod)) { status.Text="◆ Switches to "+name(mod.Desired)+" the next time you press Play."; status.Foreground=Brush("#8fe3ec"); }
    else { status.Text=mod.Desired=="off"?"Off":"● Active"+(mod.Kind==ModKind.Interface?": "+name(mod.Desired):""); status.Foreground=Brush(mod.Desired=="off"?"#6f889a":"#8fd8b4"); }
    return;
   }
   if(mod.Kind==ModKind.Required) { status.Text=mod.Reason; status.Foreground=Brush("#6f889a"); return; }
   status.Visibility=Visibility.Collapsed;
  }
  static string ThemeName(ModEntry mod,string id) { InterfaceTheme theme=mod.Themes.FirstOrDefault(x=>x.id==id); return theme==null||String.IsNullOrEmpty(theme.name)?id:theme.name; }
  static bool Pending(ModEntry mod) {
   return mod.Applied!=null && (mod.Applied!=mod.Desired || (mod.Kind==ModKind.Interface && mod.Desired=="layout" && mod.AppliedTheme!=mod.Theme));
  }
  FrameworkElement ThemePicker(ModEntry mod,TextBlock status,bool locked,out StackPanel options) {
   StackPanel row=new StackPanel { HorizontalAlignment=HorizontalAlignment.Right,Margin=new Thickness(0,9,0,0) };
   row.Children.Add(new TextBlock { Text="FULL HUD THEME",FontSize=10,Foreground=Brush("#d6b878"),HorizontalAlignment=HorizontalAlignment.Right,Margin=new Thickness(0,0,0,5) });
   options=new StackPanel { Orientation=Orientation.Horizontal,HorizontalAlignment=HorizontalAlignment.Right,IsEnabled=!locked&&mod.Desired=="layout" };
   foreach(InterfaceTheme theme in mod.Themes) {
    string id=theme.id, swatch;
    if(!ThemeSwatches.TryGetValue(id,out swatch))swatch="#93abbc";
    StackPanel label=new StackPanel { Orientation=Orientation.Horizontal };
    label.Children.Add(new Border { Width=8,Height=8,CornerRadius=new CornerRadius(4),Background=Brush(swatch),VerticalAlignment=VerticalAlignment.Center,Margin=new Thickness(0,0,5,0) });
    label.Children.Add(new TextBlock { Text=ThemeName(mod,id),FontSize=11 });
    RadioButton option=new RadioButton { Content=label,GroupName="Theme-"+mod.Name,IsChecked=mod.Theme==id,ToolTip="Full HUD colour: "+ThemeName(mod,id),
     Style=(Style)Find<FrameworkElement>("ModsOverlay").FindResource("ModTheme") };
    option.Checked+=delegate { ChangeTheme(mod,id,status); };
    options.Children.Add(option);
   }
   row.Children.Add(options);
   return row;
  }
  FrameworkElement Control(ModEntry mod,TextBlock status) {
   bool locked=busy();
   if(mod.Kind==ModKind.Interface) {
    StackPanel panel=new StackPanel { VerticalAlignment=VerticalAlignment.Center,HorizontalAlignment=HorizontalAlignment.Right };
    StackPanel segments=new StackPanel { Orientation=Orientation.Horizontal,HorizontalAlignment=HorizontalAlignment.Right };
    TextBlock hint=new TextBlock { FontSize=11,Foreground=Brush("#6f889a"),TextWrapping=TextWrapping.Wrap,TextAlignment=TextAlignment.Right,Margin=new Thickness(0,7,0,0),Width=250,HorizontalAlignment=HorizontalAlignment.Right,Text=InterfaceHints[mod.Desired] };
    StackPanel themes=null;
    foreach(string state in InterfaceStates) {
     string value=state;
     RadioButton option=new RadioButton { Content=InterfaceNames[state],GroupName="Interface-"+mod.Name,IsChecked=mod.Desired==state,IsEnabled=!locked,Style=(Style)Find<FrameworkElement>("ModsOverlay").FindResource("ModSegment") };
     option.Checked+=delegate { if(Change(mod,value,status)) { hint.Text=InterfaceHints[value]; if(themes!=null)themes.IsEnabled=value=="layout"; } };
     segments.Children.Add(option);
    }
    panel.Children.Add(segments); panel.Children.Add(hint);
    if(mod.Themes.Count>1)panel.Children.Add(ThemePicker(mod,status,locked,out themes));
    return panel;
   }
   if(mod.Kind==ModKind.Switchable) {
    CheckBox toggle=new CheckBox { Content=mod.Desired=="off"?"Disabled":"Enabled",IsChecked=mod.Desired!="off",IsEnabled=!locked,HorizontalAlignment=HorizontalAlignment.Right,VerticalAlignment=VerticalAlignment.Center,
     Style=(Style)Find<FrameworkElement>("ModsOverlay").FindResource("ModSwitch") };
    toggle.Checked+=delegate { if(Change(mod,"on",status))toggle.Content="Enabled"; };
    toggle.Unchecked+=delegate { if(Change(mod,"off",status))toggle.Content="Disabled"; };
    return toggle;
   }
   Border chip=new Border { BorderBrush=Brush(mod.Kind==ModKind.Required?"#8a6d3c":"#2e4a61"),BorderThickness=new Thickness(1),Padding=new Thickness(10,4,10,4),
    HorizontalAlignment=HorizontalAlignment.Right,VerticalAlignment=VerticalAlignment.Center,ToolTip=mod.Reason,Background=Brush("#0a1521") };
   chip.Child=new TextBlock { Text=(mod.Kind==ModKind.Required?"🔒  ":"")+mod.Lock,FontSize=10,Foreground=Brush(mod.Kind==ModKind.Required?"#d6b878":"#93abbc") };
   return chip;
  }
  bool Change(ModEntry mod,string desired,TextBlock status) {
   if(busy()) { Note("Wait for the current launch to finish before changing mods.",true); Render(); return false; }
   try { ModCatalog.SetDesired(root,mod,desired); }
   catch(Exception failure) {
    if(!(failure is IOException||failure is UnauthorizedAccessException||failure is ArgumentException||failure is InvalidOperationException))throw;
    Note(mod.Info.Title+" was not changed: "+failure.Message,true); Refresh(); return false;
   }
   Status(mod,status);
   Note(!Pending(mod)?"Saved. "+mod.Info.Title+" stays as installed.":"Saved. "+mod.Info.Title+" switches the next time you press Play"+(running()?", once every Aion client is closed.":"."),false);
   return true;
  }
  void ChangeTheme(ModEntry mod,string theme,TextBlock status) {
   if(busy()) { Note("Wait for the current launch to finish before changing mods.",true); Render(); return; }
   try { ModCatalog.SetTheme(root,mod,theme); }
   catch(Exception failure) {
    if(!(failure is IOException||failure is UnauthorizedAccessException||failure is ArgumentException||failure is InvalidOperationException))throw;
    Note(mod.Info.Title+" was not changed: "+failure.Message,true); Refresh(); return;
   }
   Status(mod,status);
   Note(!Pending(mod)?"Saved. "+mod.Info.Title+" stays as installed.":"Saved. The Full HUD uses "+ThemeName(mod,theme)+" from the next time you press Play"+(running()?", once every Aion client is closed.":"."),false);
  }
  void Note(string message,bool problem=false) {
   TextBlock note=Find<TextBlock>("ModsNote");
   if(message==null) {
    problem=error!=null||running()||busy();
    message=error??(busy()?"Aion is being prepared. Mods can be changed when the launch has finished.":running()?"Aion is running. Choices are saved and apply at the next Play after every client is closed."
     :"Choices are saved now and applied the next time you press Play. Locked mods are loaded by the game itself.");
   }
   note.Text=message; note.Foreground=Brush(problem?"#e8a08e":"#93abbc");
  }
 }
}
