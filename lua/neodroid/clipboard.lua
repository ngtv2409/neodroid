local M = {}

local utils = require("neodroid.utils")

function M.empty_clipboard()
    -- return a valid empty_clipboard when failure to avoid weird fallback
    return { { "" }, "v" }
end

function M.set(lines, regtype)
    local channel = utils.find_neodroid_channel()
    if not channel then
        utils.report_failure("no connected Neodroid client")
        return
    end

    local ok, err = pcall(vim.rpcrequest, channel, "neodroid_clipboard_set", {
        lines = lines,
        regtype = regtype,
    })
    if not ok then
        utils.report_failure("clipboard write failed: " .. tostring(err))
    end
end

function M.get()
    local channel = utils.find_neodroid_channel()
    if not channel then
        utils.report_failure("no connected Neodroid client")
        return M.empty_clipboard()
    end

    local ok, result = pcall(vim.rpcrequest, channel, "neodroid_clipboard_get")
    if not ok then
        utils.report_failure("clipboard read failed: " .. tostring(result))
        return M.empty_clipboard()
    end

    if type(result) ~= "table" or type(result.lines) ~= "table" then
        utils.report_failure("invalid clipboard response")
        return M.empty_clipboard()
    end

    local regtype = result.regtype
    if type(regtype) ~= "string" or regtype == "" then
        regtype = "v"
    end

    return { result.lines, regtype }
end

return M
