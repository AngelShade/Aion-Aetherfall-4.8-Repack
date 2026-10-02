function Get-NativeWardrobeThemePaths($Manifest) {
    if (-not $Manifest.nativeWardrobeTheme) { return @() }
    if ($Manifest.nativeWardrobeTheme -ne 'blue-v1') { throw 'Unknown native Wardrobe theme.' }
    return @('window','panel','preview','field','card','card_over','card_selected',
        'button','button_over','button_down','button_selected','button_disabled',
        'primary','primary_over','primary_down','selection') | ForEach-Object { "Textures/UI/WardrobeNative/$_.dds" }
}
