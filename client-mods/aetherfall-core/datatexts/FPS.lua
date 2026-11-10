AF.DataTexts:Register('fps', {label = 'FPS', text = function(context)
        local fps = AF.Native.Number(AFCore.FPS()); return fps and string.format('%d fps', math.floor(fps + .5))
end})
