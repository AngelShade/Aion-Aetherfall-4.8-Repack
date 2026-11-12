-- All interface chrome is applied here. Images fail softly on older resources.
local S = {
    Gold='0.839,0.722,0.471,1', Ice='0.875,0.914,0.941,1',
    Mist='0.576,0.671,0.737,1', Aether='0.561,0.890,0.925,1',
    Abyss='0.027,0.051,0.086,1', Deep='0.039,0.082,0.129,1',
    Surface='0.055,0.106,0.161,1', Raised='0.078,0.145,0.220,1',
    Line='0.122,0.204,0.278,1', Dim='0.435,0.533,0.604,1',
    Error='0.91,0.63,0.56,1', Disabled='0.58,0.39,0.39,1',
}
AF.Skin = S
function S.SetAccent(colour) S.Aether = colour end
local fixedWidths = {[24]=true,[44]=true,[52]=true,[58]=true,[60]=true,[62]=true,[74]=true,[76]=true,[80]=true,[82]=true,[86]=true,[88]=true,[98]=true,[100]=true,[116]=true,[136]=true,[158]=true}
local root, cache = 'Textures/UI/AetherfallOptions/', setmetatable({}, {__mode='k'})
local function call(object, method, ...)
    if not object or type(object[method]) ~= 'function' then return end
    return pcall(object[method], object, ...)
end
function S.Pixel(value)
    local scale = AF.Native.Scale()
    -- The native rectangle setter truncates UI units before applying scale.
    return math.max(1, math.floor((value or 1)*scale+.5) / scale)
end
function S.Image(object, state, name)
    if not object then return false end
    local images = cache[object] or {}; cache[object] = images
    if images[state] == name then return true end
    local ok = call(object, 'SetUIImage', state, name:find('/',1,true) and name or root .. name)
    if ok then images[state] = name end
    return not not ok
end
local colours, stateColours = setmetatable({}, {__mode='k'}), setmetatable({}, {__mode='k'})
function S.Text(object, token)
    if not object then return end
    local colour = S[token] or token or S.Ice
    local r, g, b = colour:match('^#(%x%x)(%x%x)(%x%x)$')
    if r then colour = string.format('%.4f,%.4f,%.4f,1', tonumber(r,16)/255, tonumber(g,16)/255, tonumber(b,16)/255) end
    if colours[object] == colour then return end
    call(object, 'SetAttr', 'textColor', colour); colours[object] = colour
end
local fonts = setmetatable({}, {__mode='k'})
function S.Font(object, name)
    if not object or fonts[object] == name then return end
    local ok = call(object, 'SetFont', name)
    if ok then fonts[object] = name end
end
local rects, lines = setmetatable({}, {__mode='k'}), setmetatable({}, {__mode='k'})
local function edges(object)
    if not object or not object.name then return end
    local found = lines[object]
    if found == nil then
        found = {}
        for _, edge in ipairs({'Top','Bottom','Left','Right'}) do found[edge] = _G[object.name .. 'Edge' .. edge] end
        if not next(found) then found = false end
        lines[object] = found
    end
    return found or nil
end
-- Controls are placed through the skin: border lines are separate widgets beside
-- the control, so they must move, show and hide with it.
local shown, texts = setmetatable({}, {__mode='k'}), setmetatable({}, {__mode='k'})
function S.Place(object, x, y, width, height)
    if not object then return end
    local rect = rects[object]
    if rect and rect[1] == x and rect[2] == y and rect[3] == width and rect[4] == height then return end
    object:SetRect(x, y, width, height)
    rects[object] = {x, y, width, height}
end
-- For a control whose own placement and visibility stay with its module (the mover boxes):
-- tell the skin where it is, and hide only its border lines.
function S.Remember(object, x, y, width, height) if object then rects[object] = {x, y, width, height} end end
function S.HideEdges(object, verify)
    local found = object and edges(object)
    if found then
        for _, line in pairs(found) do
            local state = cache[line] or {}; cache[line] = state
            if state.shown ~= false or (verify and line:IsVisible() == 1) then line:Hide(); state.shown = false end
        end
    end
end
function S.Show(object)
    if not object or shown[object] == true then return end
    object:Show(); shown[object] = true
end
function S.SetText(object, text)
    if not object then return end
    text = tostring(text)
    if texts[object] == text then return end
    object:SetText(text); texts[object] = text
end
-- Width of a text in the object's current font (interface units), or nil if unmeasured.
function S.TextWidth(object, text)
    local ok, width = call(object, 'GetTextWidth', tostring(text or ''))
    return ok and tonumber(width) or nil
end
local enabled = setmetatable({}, {__mode='k'})
function S.Enable(object, on)
    if not object or type(object.Enable) ~= 'function' then return end
    on = on and true or false
    if enabled[object] == on then return end
    pcall(object.Enable, object, on); enabled[object] = on
end
-- An edit box changes under the player's hands, so its text is never assumed.
function S.Forget(object) if object then texts[object] = nil end end
function S.Hide(object, verify)
    if not object then return end
    if shown[object] ~= false or (verify and object:IsVisible() == 1) then object:Hide(); shown[object] = false end
    S.HideEdges(object, verify)
end
function S.Border(object, width, height, token)
    local found, rect = edges(object), rects[object]
    if not found or not rect then return end
    local x, y = rect[1], rect[2]
    width = type(width) == 'number' and width or rect[3]
    height = type(height) == 'number' and height or rect[4]
    -- Keep one native UI unit at minimum: fractional thickness can truncate to zero.
    local pixel = S.Pixel(1)
    local placed = {
        Top={x,y,width,pixel}, Bottom={x,y+height-pixel,width,pixel},
        Left={x,y,pixel,height}, Right={x+width-pixel,y,pixel,height},
    }
    for edge, line in pairs(found) do
        local state = cache[line] or {}; cache[line] = state
        local key = table.concat(placed[edge], ',')
        if state.rect ~= key then call(line, 'SetRect', unpack(placed[edge])); state.rect = key end
        if type(token) == 'string' and token:match('^#%x%x%x%x%x%x$') then
            S.ImageColor(line, token, 1)
        else
            S.Image(line, 0, token == 'Gold' and 'line-gold' or token == 'Aether' and 'line-aether' or 'line')
        end
        if state.shown ~= true then call(line, 'Show'); state.shown = true end
    end
end
function S.Panel(object, width, height, raised)
    local texture=raised=='Abyss' and 'fill-abyss' or raised and 'fill-raised' or 'fill-deep'
    S.Image(object, 0, texture)
    S.Border(object, width, height, 'Line')
end
local function sized(object, texture, width, height)
    if object and not edges(object) and height == 24 and fixedWidths[width] then
        return texture .. '-' .. width .. 'x24'
    end
    return texture
end
function S.Button(object, width, height, selected, disabled)
    if type(width) == 'boolean' then selected, width, height = width, nil, nil end
    for state, texture in pairs({[0]=selected and 'button-selected' or 'button', [1]='button-over', [2]='button-down', [256]=selected and 'button-selected' or 'button', [1024]='button-disabled'}) do
        S.Image(object, state, sized(object, disabled and 'button-disabled' or texture, width, height))
    end
    S.Border(object, width, height, selected and 'Aether' or 'Line')
    S.Text(object, disabled and 'Disabled' or selected and 'Aether' or 'Ice')
    if object and not stateColours[object] then
        stateColours[object] = true
        call(object, 'SetAttr', 'textColorOver', S.Ice)
        call(object, 'SetAttr', 'textColorSelected', S.Ice)
        call(object, 'SetAttr', 'textColorDisabled', S.Disabled)
    end
end
-- A mover box: a lighter translucent fill with a bright outline, readable over the dark editor overlay.
function S.Mover(object, selected)
    for _, state in ipairs({0,256,1024}) do S.Image(object, state, selected and 'fill-aether-deep' or 'fill-raised') end
    S.Image(object, 1, 'fill-aether-deep'); S.Image(object, 2, 'fill-deep')
    S.Border(object, nil, nil, selected and 'Aether' or 'Gold')
    S.Text(object, 'Ice')
end
function S.Check(object, on, disabled)
    for _, state in ipairs({0,1,2,256,1024}) do S.Image(object, state, on and 'check-on' or 'check-off') end
    S.Text(object, disabled and 'Disabled' or 'Aether')
end
function S.Field(object, width, height, disabled)
    S.Image(object, 0, sized(object, 'field', width, height) == 'field' and 'fill-abyss' or sized(object, 'field', width, height))
    S.Border(object, width, height, 'Line')
    S.Text(object, disabled and 'Disabled' or 'Ice')
end
function S.Track(object, width, height)
    S.Image(object, 0, 'fill-abyss'); S.Border(object, width, height, 'Line')
end
function S.Fill(object, token)
    S.Image(object, 0, token == 'Gold' and 'fill-gold' or token == 'Deep' and 'fill-deep' or 'fill-aether')
end
function S.LineImage(object, token)
    S.Image(object, 0, token == 'Gold' and 'line-gold' or 'line-aether')
end
function S.Thumb(object) S.Image(object, 0, 'thumb') end
function S.Color(object, rgb)
    local r,g,b = unpack(rgb or {1,1,1})
    S.SetText(object, '###')  -- plain characters: the game font has no block glyph guarantee
    S.Text(object, tostring(r) .. ',' .. tostring(g) .. ',' .. tostring(b) .. ',1')
end

-- StaticImage has no verified RGB setter in the client framework. Select a
-- generated opaque RGB444 tile, then use Widget:SetAlpha for translucency.
-- This keeps custom colours on the same proven image path as other chrome;
-- each channel rounds to the nearest 17/255 (at most 8/255 error).
local imageAlphas = setmetatable({}, {__mode='k'})
function S.ImageColor(object, hex, alpha)
    if not object then return end
    local r, g, b = tostring(hex or ''):match('^#(%x%x)(%x%x)(%x%x)$')
    if not r then return end
    local function nibble(value) return math.floor(tonumber(value, 16) / 17 + .5) end
    S.Image(object, 0, string.format('Textures/UI/AetherfallUnitFrames/solid-%x%x%x.dds',
        nibble(r), nibble(g), nibble(b)))
    alpha = math.max(0, math.min(1, tonumber(alpha) or 1))
    if imageAlphas[object] ~= alpha then
        local ok = call(object, 'SetAlpha', alpha, false)
        if ok then imageAlphas[object] = alpha end
    end
end

-- Both AF.Skin:Button(widget, selected) and sized dot calls are public.
for _, name in ipairs({'Pixel','Image','Text','Font','Place','Remember','HideEdges','Show','SetText','Enable','Forget','Hide','Border','Panel','Mover','Button','Check','Field','Track','Fill','LineImage','Thumb','Color','ImageColor'}) do
    local method = S[name]
    S[name] = function(first, ...)
        if first == S then return method(...) end
        return method(first, ...)
    end
end
