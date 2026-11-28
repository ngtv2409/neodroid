-- Neodroid integration plugin
--
-- The app must have "Allow remote clipboard" enabled in its Integration settings.
-- When it is disabled, every clipboard operation fails with an error returned by the
-- app.

local M = {}

function M.setup()
    local clipboard = require("neodroid.clipboard")
    M.clipboard = clipboard

    local function clipboard_setup()
        vim.g.clipboard = {
            name = "neodroid",
            copy = {
                ["+"] = clipboard.set,
                ["*"] = clipboard.set,
            },
            paste = {
                ["+"] = clipboard.get,
                ["*"] = clipboard.get,
            },
            cache_enabled = 0,
        }
    end

    clipboard_setup()
    vim.api.nvim_create_user_command("NeodroidClipboardSetup", clipboard_setup, {})

    -- Re-run the clipboard provider setup so this works even when the provider
    -- was initialized before this file was sourced.
    vim.g.loaded_clipboard_provider = nil
    vim.cmd.runtime("autoload/provider/clipboard.vim")
end

return M
