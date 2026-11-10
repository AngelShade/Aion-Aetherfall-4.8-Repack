-- /afcore watch: every game window that opens for 15 seconds, with its class
-- (to tell tooltips apart). Costs nothing unless a watch was started.
local M = AF:NewModule('Watch', {needs = {'Lifecycle'}})
local S = {}
local PLUGIN_FIRST = 0x20e
function M:Start(deadline) S.deadline, S.seen = deadline, {} end
function M:Diagnostics() return AF.Copy(S) end
M:Phase('watch', function()
    if not S.deadline then return end
    for id = 1, PLUGIN_FIRST - 1 do
        local name, x, y, wd, ht, shown, class = AFCore.Dialog(id)
        if name and shown and not S.seen[id] then
            S.seen[id] = string.format('0x%x %s class 0x%x %dx%d at %d,%d', id, name ~= '' and name or '(unnamed)', class or 0, wd, ht, x, y)
        end
    end
    if AF:Call('Lifecycle', 'Ticks') >= S.deadline then
        S.deadline = nil
        local lines = {}
        for _, text in pairs(S.seen) do lines[#lines + 1] = text end
        table.sort(lines)
        DisplayChatMessage(0, 'Aetherfall watch: ' .. #lines .. ' window(s) opened', 0.56, 0.89, 0.93)
        for i = 1, math.min(#lines, 24) do DisplayChatMessage(0, lines[i], 0.56, 0.89, 0.93) end
    end
end)
