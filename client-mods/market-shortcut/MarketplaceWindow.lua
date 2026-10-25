-- Same movable, resizable frame and fitted browser as Player Companions.
PRIVATE_CASH_SHOP_MIN_W, PRIVATE_CASH_SHOP_MIN_H = 760, 520;
PrivateCashShopSize = {};

function PrivateCashShop_OnLoad()
    g_AddonName = "RelicCalc";
    PrivateCashShopSize = {};
    PrivateCashShopBrowser:CreateWebView();
    PrivateCashShop:Hide();
    PrivateCashShop:RegisterEvent("ADDON_TIMER");
end

function PrivateCashShop_Fit(load)
    g_AddonName = "RelicCalc";
    local S = PrivateCashShopSize;
    local x, y, w, h = PrivateCashShop:GetRect();
    local s = S.units or 1;
    if not w or not h or s <= 0 then
        S.view = "?";
        if load then PrivateCashShopBrowser:LoadUrlWithWebAuth(PRIVATE_CASH_SHOP_URL); end
        return;
    end
    local uw = math.max(w / s, PRIVATE_CASH_SHOP_MIN_W);
    local uh = math.max(h / s, PRIVATE_CASH_SHOP_MIN_H);
    if uw > w / s + 0.5 or uh > h / s + 0.5 then
        PrivateCashShop:SetRect(x / s, y / s, uw, uh);
        local _, _, fw, fh = PrivateCashShop:GetRect();
        if fw and fh then w, h = fw, fh; end
    end
    local size = string.format("%.0fx%.0f", w, h);
    S.pending = nil;
    if size ~= S.view then
        local recreate = S.view ~= nil;
        S.view = size;
        PrivateCashShopBrowser:SetRect(0, 0, uw, uh - 25);
        -- OnLoad already created the view at the XML's 1180x755 size.
        -- Only a settled resize needs a replacement, from the timer event.
        if recreate then
            PrivateCashShopBrowser:DestroyWebView();
            PrivateCashShopBrowser:CreateWebView();
        end
        load = true;
    end
    if load then
        S.load = nil;
        PrivateCashShopBrowser:LoadUrlWithWebAuth(PRIVATE_CASH_SHOP_URL);
    end
end

function PrivateCashShop_OnEvent(event)
    local S = PrivateCashShopSize;
    if event ~= "ADDON_TIMER" or not S.view then return; end
    g_AddonName = "RelicCalc";
    if PrivateCashShop:IsVisible() ~= 1 then return; end
    local _, _, w, h = PrivateCashShop:GetRect();
    local size = string.format("%.0fx%.0f", w, h);
    if size == S.view then
        S.pending = nil;
        if S.load then PrivateCashShop_Fit(true); end
        return;
    end
    if S.pending ~= size then S.pending = size; S.ticks = 0; return; end
    S.ticks = S.ticks + 1;
    if S.ticks >= 10 then PrivateCashShop_Fit(S.load); end
end

function PrivateCashShop_Open()
    g_AddonName = "RelicCalc";
    PrivateCashShop:Show();
    if not PrivateCashShopSize.placed then
        PrivateCashShopSize.placed = true;
        local ok, _, sw, sh = pcall(function() if AFCore and AFCore.Scale then return AFCore.Scale() end end);
        local s = (GetUIScale and GetUIScale()) or 1;
        local cx, cy = 0, 0;
        if ok and sw and sh and sw > 0 and sh > 0 and s > 0 then
            cx = math.max(0, math.floor((sw / s - 1180) / 2));
            cy = math.max(0, math.floor((sh / s - 780) / 2));
        end
        PrivateCashShop:SetRect(cx, cy, 1180, 780);
        -- Match Companions' measured GetRect units at the selected UI scale.
        local _, _, w = PrivateCashShop:GetRect();
        local units = w and w / 1180;
        PrivateCashShopSize.units = (units and units > 0.5 and units < 4) and units or 1;
    end
    if not PrivateCashShopSize.view then
        PrivateCashShop_Fit(true);
    else
        -- Keep browser replacement out of the native opening callback.
        PrivateCashShopSize.load = true;
    end
end
