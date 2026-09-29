-- Browser methods come from the client's bundled Widget.lua.
function PrivateCashShop_OnLoad()
    PrivateCashShopBrowser:CreateWebView();
    PrivateCashShop:Hide();
end

function PrivateCashShop_Open()
    PrivateCashShop:Show();
    PrivateCashShopBrowser:LoadUrlWithWebAuth(PRIVATE_CASH_SHOP_URL);
end

function PrivateMenus_Register()
    SlashCmdList["PRIVATECASHSHOP"] = PrivateCashShop_Open;
    SLASH_PRIVATECASHSHOP1 = "/privatecashshop";
    for _, entry in ipairs(PRIVATE_SERVER_MENUS) do
        RegisterMenu(entry.label, "/say ." .. entry.command, "v5_start_menu_relic_up");
    end
    RegisterMenu(PRIVATE_CASH_SHOP_LABEL, SLASH_PRIVATECASHSHOP1, "v5_start_menu_relic_up");
end
