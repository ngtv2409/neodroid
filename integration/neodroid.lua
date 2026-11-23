-- Neodroid integration.
--
-- Source this file in init.lua when vim.g.neodroid == 1
--
-- The app must have "Allow remote clipboard" enabled in its Integration settings.
-- When it is disabled, every clipboard operation fails with an error returned by the
-- app.

local M = {}

local function find_neodroid_channel()
    for _, chan in ipairs(vim.api.nvim_list_chans()) do
        if chan.client and chan.client.name == "neodroid" then
            return chan.id
        end
    end

    return nil
end

local function report_failure(message)
    -- Clipboard functions are called from a Vimscript expression. Emitting an
    -- error notification synchronously aborts that expression, so defer it.
    vim.schedule(function()
        vim.notify("[neodroid] " .. message, vim.log.levels.ERROR)
    end)
end

local function empty_clipboard()
    -- return a valid empty_clipboard when failure to avoid weird fallback
    return { { "" }, "v" }
end

local function clipboard_copy(lines, regtype)
    local channel = find_neodroid_channel()
    if not channel then
        report_failure("no connected Neodroid client")
        return
    end

    local ok, err = pcall(vim.rpcrequest, channel, "neodroid_clipboard_set", {
        lines = lines,
        regtype = regtype,
    })
    if not ok then
        report_failure("clipboard write failed: " .. tostring(err))
    end
end

local function clipboard_paste()
    local channel = find_neodroid_channel()
    if not channel then
        report_failure("no connected Neodroid client")
        return empty_clipboard()
    end

    local ok, result = pcall(vim.rpcrequest, channel, "neodroid_clipboard_get")
    if not ok then
        report_failure("clipboard read failed: " .. tostring(result))
        return empty_clipboard()
    end

    if type(result) ~= "table" or type(result.lines) ~= "table" then
        report_failure("invalid clipboard response")
        return empty_clipboard()
    end

    local regtype = result.regtype
    if type(regtype) ~= "string" or regtype == "" then
        regtype = "v"
    end

    return { result.lines, regtype }
end

function M.setup()
    vim.g.clipboard = {
        name = "neodroid",
        copy = {
            ["+"] = clipboard_copy,
            ["*"] = clipboard_copy,
        },
        paste = {
            ["+"] = clipboard_paste,
            ["*"] = clipboard_paste,
        },
        cache_enabled = 0,
    }

    -- Re-run the clipboard provider setup so this works even when the provider
    -- was initialized before this file was sourced.
    vim.g.loaded_clipboard_provider = nil
    vim.cmd.runtime("autoload/provider/clipboard.vim")
end

M.setup()

return M
