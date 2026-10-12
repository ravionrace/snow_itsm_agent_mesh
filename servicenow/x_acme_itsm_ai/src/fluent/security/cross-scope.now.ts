import '@servicenow/sdk/global'
import { CrossScopePrivilege } from '@servicenow/sdk/core'

// Record access is still checked per user by GlideRecordSecure; these only let the scope reach the tables.
// The RiskCalculator and ChangeCheckConflicts privileges are optional; their callers degrade gracefully.

CrossScopePrivilege({
    $id: Now.ID['xsp-incident-read'],
    targetScope: 'global',
    targetName: 'incident',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-incident-write'],
    targetScope: 'global',
    targetName: 'incident',
    targetType: 'sys_db_object',
    operation: 'write',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-incident-create'],
    targetScope: 'global',
    targetName: 'incident',
    targetType: 'sys_db_object',
    operation: 'create',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-problem-read'],
    targetScope: 'global',
    targetName: 'problem',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-problem-write'],
    targetScope: 'global',
    targetName: 'problem',
    targetType: 'sys_db_object',
    operation: 'write',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-problem-create'],
    targetScope: 'global',
    targetName: 'problem',
    targetType: 'sys_db_object',
    operation: 'create',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-change_request-read'],
    targetScope: 'global',
    targetName: 'change_request',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-change_request-write'],
    targetScope: 'global',
    targetName: 'change_request',
    targetType: 'sys_db_object',
    operation: 'write',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-change_request-create'],
    targetScope: 'global',
    targetName: 'change_request',
    targetType: 'sys_db_object',
    operation: 'create',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-change_task-read'],
    targetScope: 'global',
    targetName: 'change_task',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-conflict-read'],
    targetScope: 'global',
    targetName: 'conflict',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-cab_meeting-read'],
    targetScope: 'global',
    targetName: 'cab_meeting',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-cab_agenda_item-read'],
    targetScope: 'global',
    targetName: 'cab_agenda_item',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-std_change_record_producer-read'],
    targetScope: 'global',
    targetName: 'std_change_record_producer',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sysapproval_approver-read'],
    targetScope: 'global',
    targetName: 'sysapproval_approver',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sc_cat_item-read'],
    targetScope: 'global',
    targetName: 'sc_cat_item',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-item_option_new-read'],
    targetScope: 'global',
    targetName: 'item_option_new',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-io_set_item-read'],
    targetScope: 'global',
    targetName: 'io_set_item',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-question_choice-read'],
    targetScope: 'global',
    targetName: 'question_choice',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sc_req_item-read'],
    targetScope: 'global',
    targetName: 'sc_req_item',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sc_req_item-write'],
    targetScope: 'global',
    targetName: 'sc_req_item',
    targetType: 'sys_db_object',
    operation: 'write',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sc_task-read'],
    targetScope: 'global',
    targetName: 'sc_task',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-cmdb_ci-read'],
    targetScope: 'global',
    targetName: 'cmdb_ci',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-cmdb_rel_ci-read'],
    targetScope: 'global',
    targetName: 'cmdb_rel_ci',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-kb_knowledge-read'],
    targetScope: 'global',
    targetName: 'kb_knowledge',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-kb_knowledge-create'],
    targetScope: 'global',
    targetName: 'kb_knowledge',
    targetType: 'sys_db_object',
    operation: 'create',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_user-read'],
    targetScope: 'global',
    targetName: 'sys_user',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_user_group-read'],
    targetScope: 'global',
    targetName: 'sys_user_group',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_user_grmember-read'],
    targetScope: 'global',
    targetName: 'sys_user_grmember',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_journal_field-read'],
    targetScope: 'global',
    targetName: 'sys_journal_field',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sn_chg_rest-ChangeRequest'],
    targetScope: 'sn_chg_rest',
    targetName: 'ChangeRequest',
    targetType: 'sys_script_include',
    operation: 'execute',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-global-ChangeRequestStateHandler'],
    targetScope: 'global',
    targetName: 'ChangeRequestStateHandler',
    targetType: 'sys_script_include',
    operation: 'execute',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-global-RiskCalculator'],
    targetScope: 'global',
    targetName: 'RiskCalculator',
    targetType: 'sys_script_include',
    operation: 'execute',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-global-ChangeCheckConflicts'],
    targetScope: 'global',
    targetName: 'ChangeCheckConflicts',
    targetType: 'sys_script_include',
    operation: 'execute',
    status: 'allowed',
})

// Access administration (src/server/services/access-service.ts): only this app's API roles and groups that
// hold them are ever changed, after an access_admin check in code as well as the route ACL.
CrossScopePrivilege({
    $id: Now.ID['xsp-sys_group_has_role-read'],
    targetScope: 'global',
    targetName: 'sys_group_has_role',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_group_has_role-create'],
    targetScope: 'global',
    targetName: 'sys_group_has_role',
    targetType: 'sys_db_object',
    operation: 'create',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_group_has_role-delete'],
    targetScope: 'global',
    targetName: 'sys_group_has_role',
    targetType: 'sys_db_object',
    operation: 'delete',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_user_grmember-create'],
    targetScope: 'global',
    targetName: 'sys_user_grmember',
    targetType: 'sys_db_object',
    operation: 'create',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_user_grmember-delete'],
    targetScope: 'global',
    targetName: 'sys_user_grmember',
    targetType: 'sys_db_object',
    operation: 'delete',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_user_group-create'],
    targetScope: 'global',
    targetName: 'sys_user_group',
    targetType: 'sys_db_object',
    operation: 'create',
    status: 'allowed',
})

CrossScopePrivilege({
    $id: Now.ID['xsp-sys_user_role-read'],
    targetScope: 'global',
    targetName: 'sys_user_role',
    targetType: 'sys_db_object',
    operation: 'read',
    status: 'allowed',
})
