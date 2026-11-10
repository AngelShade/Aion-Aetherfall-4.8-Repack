-- Evaluated only when the options window first requests its page registry.
-- Laid out like ElvUI's profile page: a dropdown beside the button that acts on it, one
-- cell each, several to a row; explanations live in the hint line and tooltips.
AF.Config:Register('Profiles', function()
    local function get(field) return function() return AF:Call('Profiles', 'Get', field) end end
    local function set(field) return function(value) return AF:Call('Profiles', 'Set', field, value) end end
    local function choices(field) return function() return AF:Call('Profiles', 'Choices', field) end end
    local function action(method, argument) return function() return AF:Call('Profiles', method, argument) end end
    local function select(id, label, field, desc)
        return {id=id, label=label, kind='select', get=get(field), set=set(field), choices=choices(field), desc=desc}
    end
    -- A button on the control row of its neighbours, so it lines up with a dropdown or box.
    local function button(id, label, method, confirm, argument, desc, width)
        return {id=id, label=label, kind='execute', execute=action(method, argument), confirm=confirm, inline=true, desc=desc, width=width}
    end
    -- A text box whose Apply stores the text and then performs the action with it.
    local function entry(id, label, field, method, desc, width)
        return {id=id, label=label, kind='input', get=get(field), desc=desc, width=width,
            set=function(value)
                AF:Call('Profiles', 'Set', field, value)
                if method then return AF:Call('Profiles', method) end
            end}
    end
    local function note(id, text) return {id=id, label=text, kind='description', width='full'} end
    return {{id='Profiles', name='Profiles', category='Profiles', order=11,
        desc='Named layouts you can switch, copy and share between characters.', tabs={
        {id='Profile', name='Profile', groups={
            {name='Profile', options={
                select('activeProfile', 'Existing profiles', 'profile', 'Switch to another profile.'),
                entry('newProfileName', 'New profile', 'name', 'Create',
                    'Type a name and press Apply: copies the current settings and positions into a new profile and switches to it. 1-24 letters, numbers, spaces, underscores or hyphens.'),
                entry('renameProfileName', 'Rename current profile', 'rename', 'Rename', 'Type the new name and press Apply.'),
                button('resetProfile', 'Reset profile', 'ResetCurrent', true, nil, 'Put the current profile back to its defaults.'),
            }},
            {name='Copy and delete', options={
                select('copyProfileSource', 'Copy from', 'source', 'The profile whose settings and positions to copy.'),
                button('copyWholeProfile', 'Copy into current', 'CopyProfile', true, nil, 'Replace the current profile with the selected one.'),
                select('deleteProfileTarget', 'Delete a profile', 'deleteTarget', 'The profile to delete. The active profile cannot be deleted.'),
                button('deleteProfile', 'Delete', 'Delete', true, nil, 'Delete the selected profile.'),
            }},
        }},
        {id='Scope', name='Account', groups={
            {name='Account profiles', options={
                select('accountProfile', 'Account profile', 'account', 'Profiles saved for every character on this account.'),
                button('useAccountProfile', 'Use', 'UseAccount', false, nil, 'Copy the selected account profile into the current profile.'),
                button('saveAccountProfile', 'Save current to account', 'SaveAccount', false, nil, 'Store the current profile where all your characters can use it.', 2),
            }},
            {name='Copy from another character', options={
                {id='sourceCharacter', label='Character', kind='select', get=get('character'), set=set('character'),
                    choices=choices('character'), desc='A character that has used the interface on this computer.'},
                entry('characterProfileName', 'Their profile', 'characterProfile', nil, 'Leave empty to use the profile that character has active.'),
                button('copyCharacterProfile', 'Copy into current', 'CopyCharacter', true, nil, 'Replace the current profile with that character\'s layout.'),
            }},
            {name='Where settings are stored', options={
                select('scopeModule', 'Module', 'scopeModule', 'The part of the interface to store differently.'),
                {id='moduleScope', label='Store in', kind='select', choices={'Profile', 'Character', 'Account'},
                    map={'profile','character','account'}, get=get('moduleScope'), set=set('moduleScope'),
                    desc='Profile: follows the selected profile. Character: stays with this character. Account: shared by all characters.'},
            }},
        }},
        {id='Share', name='Share', groups={{name='Export and import', options={
            {id='sharingScope', label='Include', kind='select', choices={'Profile', 'Character', 'Account', 'All scopes'},
                map={'profile','character','account','all'}, get=get('sharing'), set=set('sharing'), desc='Which settings the exported code carries.'},
            button('exportProfile', 'Export', 'Export', false, nil, 'Write the code into the box below and into afui-export.cfg.'),
            button('importProfile', 'Import', 'Import', true, nil, 'Apply the code in the box. With an empty box, afui-export.cfg is read.'),
            {id='profileCode', label='Profile code', kind='input', get=get('code'), set=set('code'), width='full', maxLength=32768,
                desc='Select the code and copy it, or paste one here and press Import.'},
        }}}},
        {id='ModuleCopy', name='Modules', groups={{name='Copy one module from another profile', options={
            select('moduleCopySource', 'From profile', 'source', 'The profile to take the module\'s settings from.'),
            select('moduleCopyOwner', 'Module', 'module', 'The part of the interface to copy.'),
            button('copyModule', 'Copy module', 'CopyModule', true, nil, 'Copy only this module\'s settings into the current profile.'),
        }}}},
        {id='Layouts', name='Layouts', groups={{name='Ready-made layouts', options={
            {id='installDPS', label='DPS', kind='execute', execute=action('Install', 'DPS'), confirm=true,
                desc='The standard interface with default frame placement.'},
            {id='installHealer', label='Healer', kind='execute', execute=action('Install', 'Healer'), confirm=true,
                desc='Party, target of target and timers shown, with the party near the centre.'},
            {id='installMinimal', label='Minimal', kind='execute', execute=action('Install', 'Minimal'), confirm=true,
                desc='Hides data panels, extra quick bars, target timers and the XP bar.'},
            note('layoutHelp', 'A layout resets the profile of the same name. Your other profiles are kept. Hover a button to see what it does.'),
        }}}},
    }}}
end)
