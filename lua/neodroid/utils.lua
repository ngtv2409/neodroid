local M = {}

function M.find_neodroid_channel()
    for _, chan in ipairs(vim.api.nvim_list_chans()) do
        if chan.client and chan.client.name == "neodroid" then
            return chan.id
        end
    end

    return nil
end

function M.report_failure(message)
    -- Clipboard functions are called from a Vimscript expression. Emitting an
    -- error notification synchronously aborts that expression, so defer it.
    vim.schedule(function()
        vim.notify("[neodroid] " .. message, vim.log.levels.ERROR)
    end)
end

return M
