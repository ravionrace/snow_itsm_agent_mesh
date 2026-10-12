import '@servicenow/sdk/global'

declare global {
    namespace Now {
        namespace Internal {
            interface Keys extends KeysRegistry {
                explicit: {
                    'acl-access-admin-api': {
                        table: 'sys_security_acl'
                        id: 'dfb17e184e034c6dace0c5ea37da07ae'
                    }
                    'acl-agent-api': {
                        table: 'sys_security_acl'
                        id: 'd09f71530985480991b1dc528361d30b'
                        deleted: true
                    }
                    'acl-assistant-page': {
                        table: 'sys_security_acl'
                        id: '9a8aa2bc7c4b44bdb3de43b9052fd9a2'
                    }
                    'acl-audit-create': {
                        table: 'sys_security_acl'
                        id: '3044da0804d64a65965fe7ffa0c3f94a'
                    }
                    'acl-audit-delete': {
                        table: 'sys_security_acl'
                        id: 'ab87fad9de8d46a9ba33fdf327bc7dc6'
                    }
                    'acl-audit-field-read': {
                        table: 'sys_security_acl'
                        id: '4be53d126777402486e970f5e4ffafa1'
                    }
                    'acl-audit-read': {
                        table: 'sys_security_acl'
                        id: '1774d36367f04a1bb8a8a1e355705c09'
                    }
                    'acl-audit-write': {
                        table: 'sys_security_acl'
                        id: 'f9e5f943c27c46368b07350098424e86'
                    }
                    'acl-auditor-api': {
                        table: 'sys_security_acl'
                        id: 'f3d2d3dc00d3429dbb0e7067a3e7d758'
                    }
                    'acl-authenticated-api': {
                        table: 'sys_security_acl'
                        id: 'f80ee75addf34968aa5f2e7e6886a5be'
                    }
                    'acl-catalog-read': {
                        table: 'sys_security_acl'
                        id: '922b937642514902aeb3e9a74c999867'
                    }
                    'acl-catalog-write': {
                        table: 'sys_security_acl'
                        id: 'b409448da240419cadac982f19811291'
                    }
                    'acl-change-read': {
                        table: 'sys_security_acl'
                        id: '00bed5fed3394edb8d8944c94bfc0e4a'
                    }
                    'acl-change-write': {
                        table: 'sys_security_acl'
                        id: '201036708a3649ee819b6681920aa9dc'
                    }
                    'acl-chat-api': {
                        table: 'sys_security_acl'
                        id: '4e4e51c08efd4a258c2c9695733f4ac2'
                    }
                    'acl-cmdb-read': {
                        table: 'sys_security_acl'
                        id: 'ba8fbf74d5f648f992cebb9a835cc83b'
                    }
                    'acl-console-api': {
                        table: 'sys_security_acl'
                        id: '671165bae2a741339382be1fc09ebe8c'
                        deleted: true
                    }
                    'acl-console-page': {
                        table: 'sys_security_acl'
                        id: 'ce7ba3e5c61044d0abf38a936d3fe904'
                    }
                    'acl-incident-read': {
                        table: 'sys_security_acl'
                        id: '0d5b84b7f57f4cbc92599814f273cc5f'
                    }
                    'acl-incident-write': {
                        table: 'sys_security_acl'
                        id: '181791bfe67a435bb8301466ce09ad99'
                    }
                    'acl-kb-read': {
                        table: 'sys_security_acl'
                        id: '2c1671f3524b42c1bb961fcca75f8a04'
                    }
                    'acl-problem-read': {
                        table: 'sys_security_acl'
                        id: 'bcedb7f1995a437f84d9bb4865943e1e'
                    }
                    'acl-problem-write': {
                        table: 'sys_security_acl'
                        id: '5547ff3e61c94a32a2ac86e826efc438'
                    }
                    'api-access': {
                        table: 'sys_ws_definition'
                        id: 'c366a93fdb6f45cdadb3026b65b687ae'
                    }
                    'api-access-add_member': {
                        table: 'sys_ws_operation'
                        id: '0d01090e26ce4ef7af0f1b284748136a'
                    }
                    'api-access-apis': {
                        table: 'sys_ws_operation'
                        id: 'd3ab1c2927754a6183eeb1233dce3047'
                    }
                    'api-access-create_group': {
                        table: 'sys_ws_operation'
                        id: '78926ae5286f44d4a6cca89129fa7f5f'
                    }
                    'api-access-grant': {
                        table: 'sys_ws_operation'
                        id: '7a091358f0bd49419b62d0bd5bd63188'
                    }
                    'api-access-groups': {
                        table: 'sys_ws_operation'
                        id: '5c51d3761cb845fc9bafa674ad1ef57d'
                    }
                    'api-access-me': {
                        table: 'sys_ws_operation'
                        id: 'fff44da8ec1e4d4883cc8d07c81f553e'
                    }
                    'api-access-members': {
                        table: 'sys_ws_operation'
                        id: 'f459119b8ec14532acba3e13289657b4'
                    }
                    'api-access-remove_member': {
                        table: 'sys_ws_operation'
                        id: 'f900e105acd445c5855f2bd56fb44d80'
                    }
                    'api-access-revoke': {
                        table: 'sys_ws_operation'
                        id: '2e41c41c33dd4cfd8f89fa24103d1002'
                    }
                    'api-access-users': {
                        table: 'sys_ws_operation'
                        id: '9a1bd3e48a6a4ee29b61ab8afeab04cb'
                    }
                    'api-access-v1': {
                        table: 'sys_ws_version'
                        id: '82d5229f8baa46029101704920cf8003'
                    }
                    'api-catalog': {
                        table: 'sys_ws_definition'
                        id: 'f549e9c861ea41f3b92f6b927073715c'
                    }
                    'api-catalog-cancel': {
                        table: 'sys_ws_operation'
                        id: '287dd41c4cf247769f2c423b681574ec'
                    }
                    'api-catalog-form': {
                        table: 'sys_ws_operation'
                        id: 'a47b7466747a48d4aae590a5a093834f'
                    }
                    'api-catalog-items': {
                        table: 'sys_ws_operation'
                        id: 'a2e99951f3c94e679cf126bc28633256'
                    }
                    'api-catalog-my_requests': {
                        table: 'sys_ws_operation'
                        id: 'e15e5ea796464e6293eeb09609bfbcbb'
                    }
                    'api-catalog-order': {
                        table: 'sys_ws_operation'
                        id: 'ac2a415f6c654f71adbf8db275fa44bc'
                    }
                    'api-catalog-ritm': {
                        table: 'sys_ws_operation'
                        id: '190e5fc97f72407d8af39f39c8330148'
                    }
                    'api-catalog-v1': {
                        table: 'sys_ws_version'
                        id: 'd0fe3637b16c4062a746bd3735c785a0'
                    }
                    'api-catalog-validate': {
                        table: 'sys_ws_operation'
                        id: 'b4d5c2b5a0d241b68dfba32b0bbb4c22'
                    }
                    'api-change': {
                        table: 'sys_ws_definition'
                        id: 'bad07f3041ac49ada64750ba165712b6'
                    }
                    'api-change-cab_schedule': {
                        table: 'sys_ws_operation'
                        id: 'e4d9862c589c4aa6a1783897fc1cb6bf'
                    }
                    'api-change-conflicts': {
                        table: 'sys_ws_operation'
                        id: 'c1f11b833d1f4f7f85bd735198ea7b2e'
                    }
                    'api-change-create': {
                        table: 'sys_ws_operation'
                        id: '398685053bd84d28bf1225e4881e1996'
                    }
                    'api-change-get': {
                        table: 'sys_ws_operation'
                        id: '019efedc3f684e26b806126f5c29aae7'
                    }
                    'api-change-move_to_state': {
                        table: 'sys_ws_operation'
                        id: 'd6c8df3e80be485690032a206a398d06'
                    }
                    'api-change-request_approval': {
                        table: 'sys_ws_operation'
                        id: 'a0dc668330cb46f2acdb12ab03a59f90'
                    }
                    'api-change-risk': {
                        table: 'sys_ws_operation'
                        id: '5586127cf6324254a9f1c8a6f020a32e'
                    }
                    'api-change-templates': {
                        table: 'sys_ws_operation'
                        id: 'f06c3a40af744d0a9fcf01205bf34f26'
                    }
                    'api-change-v1': {
                        table: 'sys_ws_version'
                        id: 'bf5c743071f64cb1bc9bf11016f19662'
                    }
                    'api-chat': {
                        table: 'sys_ws_definition'
                        id: 'da1e99c21fa34e19a7e16cb22d73fcf0'
                    }
                    'api-chat-decide': {
                        table: 'sys_ws_operation'
                        id: '5709d4a80f2345c5985b42239c7f4fd6'
                    }
                    'api-chat-session': {
                        table: 'sys_ws_operation'
                        id: '8339088174284a939f1ee832c4bd0fa3'
                    }
                    'api-chat-start_turn': {
                        table: 'sys_ws_operation'
                        id: '4104fef0b08445b1bbe3a39009c92427'
                    }
                    'api-chat-turn': {
                        table: 'sys_ws_operation'
                        id: '19578e68cb7b446685f655a9e1b14732'
                    }
                    'api-chat-v1': {
                        table: 'sys_ws_version'
                        id: '9550d1c20312434196268f82f4f67fa9'
                    }
                    'api-cmdb': {
                        table: 'sys_ws_definition'
                        id: '51b4b0e9f9414cca9011b02a499cb84b'
                    }
                    'api-cmdb-ci': {
                        table: 'sys_ws_operation'
                        id: 'ce8625d0ee2348539051b508b77d0efa'
                    }
                    'api-cmdb-ci_by_name': {
                        table: 'sys_ws_operation'
                        id: '539f6cc9149c455dadde407efa71208f'
                    }
                    'api-cmdb-groups': {
                        table: 'sys_ws_operation'
                        id: '4425210113d546ebb47419b2409449f5'
                    }
                    'api-cmdb-impacted_services': {
                        table: 'sys_ws_operation'
                        id: '71a5aa983fcc49078acefe266f8e8eeb'
                    }
                    'api-cmdb-users': {
                        table: 'sys_ws_operation'
                        id: 'ae442142544a4f5bb22f1814f3c3c576'
                    }
                    'api-cmdb-v1': {
                        table: 'sys_ws_version'
                        id: 'aecacbe224484de1b065310d43591bad'
                    }
                    'api-console': {
                        table: 'sys_ws_definition'
                        id: '9f7c06985c46448f8207fe18a9a53629'
                    }
                    'api-console-summary': {
                        table: 'sys_ws_operation'
                        id: '9998cbbdd9254fb380aadbd25b10a43b'
                    }
                    'api-console-v1': {
                        table: 'sys_ws_version'
                        id: '198d61d90cce4311ba0325ce15cf0a77'
                    }
                    'api-incident': {
                        table: 'sys_ws_definition'
                        id: 'db02e71b42ef4b42966ec208b665632a'
                    }
                    'api-incident-add_note': {
                        table: 'sys_ws_operation'
                        id: '9b9d23094e1a4246a0e70819125a96c6'
                    }
                    'api-incident-assign': {
                        table: 'sys_ws_operation'
                        id: '2935afbd7bfa442d9b215e0d8f75d497'
                    }
                    'api-incident-create': {
                        table: 'sys_ws_operation'
                        id: '89f56e8a949f438383ecdbb0f24e3ee0'
                    }
                    'api-incident-get': {
                        table: 'sys_ws_operation'
                        id: '9dfc8fade9a146808746960367a1c913'
                    }
                    'api-incident-my_groups': {
                        table: 'sys_ws_operation'
                        id: 'e0e3fe5fb0c74168842c9afda81b5b07'
                    }
                    'api-incident-resolve': {
                        table: 'sys_ws_operation'
                        id: '67b2f9b4028340ba9ec4ff595b77b668'
                    }
                    'api-incident-search': {
                        table: 'sys_ws_operation'
                        id: '31254a2efa6e4b3f81e75a41e7d49d32'
                    }
                    'api-incident-similar': {
                        table: 'sys_ws_operation'
                        id: '2a74da68ff8242c0861e9d408ab79c2c'
                    }
                    'api-incident-update': {
                        table: 'sys_ws_operation'
                        id: '494051d7d0a34fb2a743fdcc440f63b9'
                    }
                    'api-incident-v1': {
                        table: 'sys_ws_version'
                        id: '4de6c96f2e9947a182873f94443b4924'
                    }
                    'api-kb': {
                        table: 'sys_ws_definition'
                        id: '0f6f1ab732134ae390bc34d22264f555'
                    }
                    'api-kb-articles': {
                        table: 'sys_ws_operation'
                        id: '665c7d4aaf11441f87b20bdd88ae5874'
                    }
                    'api-kb-v1': {
                        table: 'sys_ws_version'
                        id: 'd0490c0bf8ed42978c649d73259f360e'
                    }
                    'api-problem': {
                        table: 'sys_ws_definition'
                        id: '4bc5d3be40ea4f899700f33eac1ec5e2'
                    }
                    'api-problem-create': {
                        table: 'sys_ws_operation'
                        id: '52868714aa764b43afe8cf59f1a3ebe2'
                    }
                    'api-problem-get': {
                        table: 'sys_ws_operation'
                        id: '3b267130e14e4eb99a1e7395794a562a'
                    }
                    'api-problem-known_error': {
                        table: 'sys_ws_operation'
                        id: '8dd15b7f9dab4baca749e8f9c1fb8e83'
                    }
                    'api-problem-known_errors': {
                        table: 'sys_ws_operation'
                        id: '22da8749bcfa488bbf0fac5fa7b1a3b7'
                    }
                    'api-problem-link_incidents': {
                        table: 'sys_ws_operation'
                        id: '945722b04a294985b498587e5a17c8d6'
                    }
                    'api-problem-root_cause': {
                        table: 'sys_ws_operation'
                        id: '73c377b8ff2c437d8326fbd9617d9a95'
                    }
                    'api-problem-v1': {
                        table: 'sys_ws_version'
                        id: 'e454e083800a4237af87bc302b9c362f'
                    }
                    'api-problem-workaround': {
                        table: 'sys_ws_operation'
                        id: '59e6f3e1871b4aff8cc6fe45fc469b16'
                    }
                    'assets/main-Bbxah2OP.css': {
                        table: 'sys_ux_theme_asset'
                        id: 'a97d340014f64d389ac8a9d50073efe0'
                        deleted: true
                    }
                    'assets/main-BJx0O2_X.css': {
                        table: 'sys_ux_theme_asset'
                        id: '3e76e2598008479c94238b5b1707864d'
                        deleted: true
                    }
                    'assets/main-BMTjAima.css': {
                        table: 'sys_ux_theme_asset'
                        id: '10a671b7b10244eebbf5fce988e761e1'
                        deleted: true
                    }
                    'assets/main-CM0ynz93.css': {
                        table: 'sys_ux_theme_asset'
                        id: '79727da77b3b4ef0bdcb86bc82f8a1e6'
                    }
                    'assets/main-DgfmLF5t.css': {
                        table: 'sys_ux_theme_asset'
                        id: 'af81c5cbec314c5ba7a39826af3508d4'
                    }
                    'auth-scope-mesh-api': {
                        table: 'sys_auth_scope'
                        id: '2471c66186234c30812d9c82ab40430f'
                    }
                    bom_json: {
                        table: 'sys_module'
                        id: 'df660fb37d064abf8553a4db13f845ce'
                    }
                    'menu-itsm-ai': {
                        table: 'sys_app_application'
                        id: '7c406038206546eda8b1e1779303cd75'
                    }
                    'module-access': {
                        table: 'sys_app_module'
                        id: 'fcd8d9f819904125a8e700d67c5376b3'
                    }
                    'module-assistant': {
                        table: 'sys_app_module'
                        id: '90f08277b9b2444eb018d187b7c5ed3b'
                    }
                    'module-audit': {
                        table: 'sys_app_module'
                        id: 'ed27144b846a4ccaa1e112762c0abff1'
                    }
                    'module-console': {
                        table: 'sys_app_module'
                        id: 'a48ca37f0647438ca1bd710113cd5339'
                    }
                    package_json: {
                        table: 'sys_module'
                        id: '72e7f41d4c0849559378b623dcffef37'
                    }
                    'prop-cause-ci-field': {
                        table: 'sys_properties'
                        id: 'f773784359d04c3ab96a4afee18b2cf9'
                    }
                    'prop-known-error-kb': {
                        table: 'sys_properties'
                        id: '38a5330fc2584ceaa3063e9196092e38'
                    }
                    'prop-mesh-channel-secret': {
                        table: 'sys_properties'
                        id: 'a660695230144ed497bf08123cf8d6ae'
                    }
                    'prop-mesh-endpoint': {
                        table: 'sys_properties'
                        id: '2d9ed692b07f441ba513dc2038409187'
                    }
                    'src_server_lib_agent-api_ts': {
                        table: 'sys_module'
                        id: '0c9fa69aca5b4d1080f1d92e4efec5cd'
                    }
                    'src_server_lib_api-catalog_ts': {
                        table: 'sys_module'
                        id: '3cf0cb3d02614467a117d62215f3c98d'
                    }
                    'src_server_lib_channel-signature_ts': {
                        table: 'sys_module'
                        id: '903e9aeffccf47b0aea3f6d10f09241e'
                    }
                    src_server_rest_handlers_ts: {
                        table: 'sys_module'
                        id: 'acce164910db4056a8e6a3e4589b299a'
                    }
                    'src_server_services_access-service_ts': {
                        table: 'sys_module'
                        id: 'f84a26701dce4ba5bd24863acfe5b94b'
                    }
                    'src_server_services_catalog-service_ts': {
                        table: 'sys_module'
                        id: '8f6f2f4852c645fdab00608417d3526a'
                    }
                    'src_server_services_change-apis_ts': {
                        table: 'sys_module'
                        id: '17bd9ab465fe46deba89e6bab6f5721f'
                    }
                    'src_server_services_change-service_ts': {
                        table: 'sys_module'
                        id: '5678db7005ae4c12823aa3b77f99f612'
                    }
                    'src_server_services_change-states_ts': {
                        table: 'sys_module'
                        id: '4285aaf360374d77a713d56fdbc02439'
                    }
                    'src_server_services_chat-proxy-service_ts': {
                        table: 'sys_module'
                        id: '09fd2b7e523840d886b8e7f5fe8c6db6'
                    }
                    'src_server_services_cmdb-kb-service_ts': {
                        table: 'sys_module'
                        id: '53bdbccc460d46bbabe6a9c9caec5dea'
                    }
                    'src_server_services_console-service_ts': {
                        table: 'sys_module'
                        id: 'ea395f3980164a05a2bb90a3739941ba'
                    }
                    'src_server_services_incident-service_ts': {
                        table: 'sys_module'
                        id: '2cb0c3783076460f9d24275e5d62213f'
                    }
                    'src_server_services_problem-service_ts': {
                        table: 'sys_module'
                        id: 'b1aeee4f1758440381bd028908043f80'
                    }
                    'xsp-cab_agenda_item-read': {
                        table: 'sys_scope_privilege'
                        id: '88b2bc31f61d453c993f51481e4ecc05'
                    }
                    'xsp-cab_meeting-read': {
                        table: 'sys_scope_privilege'
                        id: 'be44919cf3164dac81922abb2e67a61a'
                    }
                    'xsp-change_request-create': {
                        table: 'sys_scope_privilege'
                        id: 'e4759d78537f4e909ce15c049cc73e48'
                    }
                    'xsp-change_request-read': {
                        table: 'sys_scope_privilege'
                        id: '84a07837d2c34c19bd6299dedba53510'
                    }
                    'xsp-change_request-write': {
                        table: 'sys_scope_privilege'
                        id: 'acc3647e00d24f8db72cc22e9bf722ed'
                    }
                    'xsp-change_task-read': {
                        table: 'sys_scope_privilege'
                        id: '76fa5c685f0b4b5aa8be32a21dd888cf'
                    }
                    'xsp-cmdb_ci-read': {
                        table: 'sys_scope_privilege'
                        id: 'b56a051572b74bc8af90c8d35f759c7f'
                    }
                    'xsp-cmdb_rel_ci-read': {
                        table: 'sys_scope_privilege'
                        id: '9ae8e7300ac046b18cd4f5335af452b5'
                    }
                    'xsp-conflict-read': {
                        table: 'sys_scope_privilege'
                        id: '1214aa32056b4cfdb11c24f8e914c4c6'
                    }
                    'xsp-global-ChangeCheckConflicts': {
                        table: 'sys_scope_privilege'
                        id: 'a0eb311f17924028acb1d6cbaa057812'
                    }
                    'xsp-global-ChangeRequestStateHandler': {
                        table: 'sys_scope_privilege'
                        id: 'a76424fda8ce4eff9d00ff49f5878e1b'
                    }
                    'xsp-global-RiskCalculator': {
                        table: 'sys_scope_privilege'
                        id: '00161f5f099e4ca5ad00744d9566dd72'
                    }
                    'xsp-incident-create': {
                        table: 'sys_scope_privilege'
                        id: '53c76e6e031b4da7a551d0adfc36caa2'
                    }
                    'xsp-incident-read': {
                        table: 'sys_scope_privilege'
                        id: 'e9b89920cd484486bc121c7ab612f61f'
                    }
                    'xsp-incident-write': {
                        table: 'sys_scope_privilege'
                        id: 'ee7a5af5c1b848959d814424a04b4074'
                    }
                    'xsp-io_set_item-read': {
                        table: 'sys_scope_privilege'
                        id: '99a2064305714f96afed5fb99e540022'
                    }
                    'xsp-item_option_new-read': {
                        table: 'sys_scope_privilege'
                        id: 'c5339c48792e4c03be88dd8eb41b6e96'
                    }
                    'xsp-kb_knowledge-create': {
                        table: 'sys_scope_privilege'
                        id: '628023dbacd2480480acda6b1f4c939e'
                    }
                    'xsp-kb_knowledge-read': {
                        table: 'sys_scope_privilege'
                        id: '3ecbce045c03490bb69686a9a9575449'
                    }
                    'xsp-problem-create': {
                        table: 'sys_scope_privilege'
                        id: 'a6b6c56beddf4241a0581b2430733866'
                    }
                    'xsp-problem-read': {
                        table: 'sys_scope_privilege'
                        id: 'a45b8b3b9e9a408bbdcc45c597392d94'
                    }
                    'xsp-problem-write': {
                        table: 'sys_scope_privilege'
                        id: '3af2e75143fe40ad95e0cb807bc0e494'
                    }
                    'xsp-question_choice-read': {
                        table: 'sys_scope_privilege'
                        id: 'c83eafc3ec844250a67b9d693633412c'
                    }
                    'xsp-sc_cat_item-read': {
                        table: 'sys_scope_privilege'
                        id: '64391f1a22964e93a745223064b81c04'
                    }
                    'xsp-sc_req_item-read': {
                        table: 'sys_scope_privilege'
                        id: '3241a34d163b44ddbf03032c5e31cb0e'
                    }
                    'xsp-sc_req_item-write': {
                        table: 'sys_scope_privilege'
                        id: 'fa151f4ea0bf4a4abf5de46f430a840c'
                    }
                    'xsp-sc_task-read': {
                        table: 'sys_scope_privilege'
                        id: 'd8973de3e4234658a0cc946f7eab18ec'
                    }
                    'xsp-sn_chg_rest-ChangeRequest': {
                        table: 'sys_scope_privilege'
                        id: '65393c3dec07494bba7b98d2ae805383'
                    }
                    'xsp-std_change_record_producer-read': {
                        table: 'sys_scope_privilege'
                        id: 'bd72fdab9efb41d19a775169add7da31'
                    }
                    'xsp-sys_group_has_role-create': {
                        table: 'sys_scope_privilege'
                        id: 'e34b26561e114ca5ac9bc83de8a6109a'
                    }
                    'xsp-sys_group_has_role-delete': {
                        table: 'sys_scope_privilege'
                        id: 'f543603321bd47c5a82a51e6ef477a60'
                    }
                    'xsp-sys_group_has_role-read': {
                        table: 'sys_scope_privilege'
                        id: '7fe5f6aa1a7746c7b7fdec9aac39a647'
                    }
                    'xsp-sys_journal_field-read': {
                        table: 'sys_scope_privilege'
                        id: '1bc1642607a743afbe098932152175cf'
                    }
                    'xsp-sys_user_grmember-create': {
                        table: 'sys_scope_privilege'
                        id: '36de564e431e494aaeb2ade4ade49c39'
                    }
                    'xsp-sys_user_grmember-delete': {
                        table: 'sys_scope_privilege'
                        id: 'e23b866cddce4037a96cf3dbcd5c3e30'
                    }
                    'xsp-sys_user_grmember-read': {
                        table: 'sys_scope_privilege'
                        id: '86a573e61ec140b78b454cd91806df0f'
                    }
                    'xsp-sys_user_group-create': {
                        table: 'sys_scope_privilege'
                        id: '29808558a8e846eaa536414f2e482565'
                    }
                    'xsp-sys_user_group-read': {
                        table: 'sys_scope_privilege'
                        id: '0f5026494b87427cb66a578137307b1b'
                    }
                    'xsp-sys_user_role-read': {
                        table: 'sys_scope_privilege'
                        id: 'fc5355e0be2f4b008ae63427901f20dc'
                    }
                    'xsp-sys_user-read': {
                        table: 'sys_scope_privilege'
                        id: '4c7d430279794acca199fbf5f121adce'
                    }
                    'xsp-sysapproval_approver-read': {
                        table: 'sys_scope_privilege'
                        id: '33aa18936e0b4b6bbc29e2f8a04d8df9'
                    }
                }
                composite: [
                    {
                        table: 'sys_ux_lib_asset'
                        id: '0285a91c512e471fb2c166021c1937b1'
                        key: {
                            name: 'x_1320561_itsm_ai/AuditList'
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: '04b8e5fad33b49589da8781409c13368'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'user'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: '055e22ceedde425b9315635547d900e2'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'record_table'
                            language: 'en'
                        }
                    },
                    {
                        table: 'ua_table_licensing_config'
                        id: '06bf6d67f32143da82228d7664542264'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '0f0b0173deca499988b937c02a2508f6'
                        key: {
                            sys_security_acl: 'dfb17e184e034c6dace0c5ea37da07ae'
                            sys_user_role: {
                                id: '6f9950a9438e4fe08f7a4ba99683c04a'
                                key: {
                                    name: 'x_1320561_itsm_ai.access_admin'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: '0f70c9c910d04bc59187d3a50a222aff'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'request_id'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '107e6baa14434c5b8c6189dde1702a84'
                        key: {
                            name: 'x_1320561_itsm_ai.problem_read'
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '1140dffad8cf453d9732bb2fb11a5822'
                        key: {
                            name: 'x_1320561_itsm_ai.incident_read'
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: '146f4605b64147c89e41ef8ed9eb2932'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'record_table'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: '1a9aab93215546cca712462c74db698a'
                        key: {
                            role: {
                                id: 'ac9a5c99afb946b3886797894bb5fdb1'
                                key: {
                                    name: 'x_1320561_itsm_ai.problem_write'
                                }
                            }
                            contains: {
                                id: '107e6baa14434c5b8c6189dde1702a84'
                                key: {
                                    name: 'x_1320561_itsm_ai.problem_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '1ae0999549f540e4bc81139e2cbab79b'
                        key: {
                            name: 'x_1320561_itsm_ai.chat_user'
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '205ba15032c4405594ddaf28edfc38f6'
                        key: {
                            name: 'x_1320561_itsm_ai.change_write'
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '21914170032d4bd581b2e7aeb10b1563'
                        key: {
                            name: 'x_1320561_itsm_ai.agent_user'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '22bf7578b83842de8dcc7127dc30af9b'
                        key: {
                            sys_security_acl: '2c1671f3524b42c1bb961fcca75f8a04'
                            sys_user_role: {
                                id: '463a952ad99d4e478f0224241eee5e35'
                                key: {
                                    name: 'x_1320561_itsm_ai.kb_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_index'
                        id: '22c3745d89ca43fca35028297326f8ff'
                        key: {
                            logical_table_name: 'x_1320561_itsm_ai_tool_audit'
                            col_name_string: 'record_number'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: '24867665ebf84ccdb6abfa9cdf2cd0ea'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: '205ba15032c4405594ddaf28edfc38f6'
                                key: {
                                    name: 'x_1320561_itsm_ai.change_write'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: '261df43681894f37a1bba49cfd66ef1c'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'duration_ms'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_ux_lib_asset'
                        id: '29fd28b67c304e2a9426277ce22d75b3'
                        key: {
                            name: 'x_1320561_itsm_ai/Textarea.js.map'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: '2d93efb3016b41ce988be154a145982f'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'http_status'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '2e5e63ef40d1427e903227aead43da4f'
                        key: {
                            sys_security_acl: 'ab87fad9de8d46a9ba33fdf327bc7dc6'
                            sys_user_role: {
                                id: 'bacc16fe07ec4357b6fc201f2f4c277a'
                                key: {
                                    name: 'admin'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_ux_lib_asset'
                        id: '2e79ee54e2f14dd5af9ef5db7f4e95d6'
                        key: {
                            name: 'x_1320561_itsm_ai/AuditList.js.map'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: '2eb8e10715fb46e2aa248c097f9a607d'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: '728d5bfc8f684648935d01f9c0792653'
                                key: {
                                    name: 'x_1320561_itsm_ai.incident_write'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '32c5248e841244c5bf88a58610dc9400'
                        key: {
                            sys_security_acl: '5547ff3e61c94a32a2ac86e826efc438'
                            sys_user_role: {
                                id: 'ac9a5c99afb946b3886797894bb5fdb1'
                                key: {
                                    name: 'x_1320561_itsm_ai.problem_write'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: '361482f44f634202b114b6bf35c0fe31'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: '463a952ad99d4e478f0224241eee5e35'
                                key: {
                                    name: 'x_1320561_itsm_ai.kb_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: '38f9f3e58d44498baec1cbdc42a60b6c'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'record_sys_id'
                        }
                    },
                    {
                        table: 'sys_index'
                        id: '40ef2443acc5485e9b1d0de4d33d0c14'
                        key: {
                            logical_table_name: 'x_1320561_itsm_ai_tool_audit'
                            col_name_string: 'idempotency_key,user'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: '454cecc1335a4fad9bbcf5f4eeea91e0'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: '698900b3a44f4b94ac8ca92da1bb5815'
                                key: {
                                    name: 'x_1320561_itsm_ai.catalog_write'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '463a952ad99d4e478f0224241eee5e35'
                        key: {
                            name: 'x_1320561_itsm_ai.kb_read'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: '50b8b10a74d34cb2a34b752621c80c17'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'idempotency_key'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: '51c361fc3f9648cbacf4fc3d07fda4ee'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: '5f3a0d576c8e45dfb39aa3eb3c8d5c61'
                                key: {
                                    name: 'x_1320561_itsm_ai.change_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '5343aba9ae9a4e69b930db616e30652c'
                        key: {
                            sys_security_acl: 'ba8fbf74d5f648f992cebb9a835cc83b'
                            sys_user_role: {
                                id: 'd7d2829ac537412a801230c948457929'
                                key: {
                                    name: 'x_1320561_itsm_ai.cmdb_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: '54b9e16da8ad42048bf6d3a3030ba90b'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: '896680a5a1cf492da9fa53b9b79115a4'
                                key: {
                                    name: 'x_1320561_itsm_ai.catalog_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: '56418eab546c4a1a83c2877790946e83'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'session_id'
                        }
                    },
                    {
                        table: 'sys_db_object'
                        id: '5757c4bb7f464457878010b613f90fb5'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '5d6b4588fa174da0ab836b463c97d59b'
                        key: {
                            sys_security_acl: '4e4e51c08efd4a258c2c9695733f4ac2'
                            sys_user_role: {
                                id: '1ae0999549f540e4bc81139e2cbab79b'
                                key: {
                                    name: 'x_1320561_itsm_ai.chat_user'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '5f3a0d576c8e45dfb39aa3eb3c8d5c61'
                        key: {
                            name: 'x_1320561_itsm_ai.change_read'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: '60b732991c454299a5ffe25b1fc0a234'
                        key: {
                            role: {
                                id: '205ba15032c4405594ddaf28edfc38f6'
                                key: {
                                    name: 'x_1320561_itsm_ai.change_write'
                                }
                            }
                            contains: {
                                id: '5f3a0d576c8e45dfb39aa3eb3c8d5c61'
                                key: {
                                    name: 'x_1320561_itsm_ai.change_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: '614683180bfb4473b5a73434aecff31a'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'request_id'
                        }
                    },
                    {
                        table: 'sys_index'
                        id: '62ab07d2f13548c3a477fda54b4015b5'
                        key: {
                            logical_table_name: 'x_1320561_itsm_ai_tool_audit'
                            col_name_string: 'sys_created_on,api'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '68262774b3a44c63bc3f7e5ac3ad19a6'
                        key: {
                            sys_security_acl: 'f9e5f943c27c46368b07350098424e86'
                            sys_user_role: {
                                id: 'bacc16fe07ec4357b6fc201f2f4c277a'
                                key: {
                                    name: 'admin'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '698900b3a44f4b94ac8ca92da1bb5815'
                        key: {
                            name: 'x_1320561_itsm_ai.catalog_write'
                        }
                    },
                    {
                        table: 'sys_choice'
                        id: '6a09444cf5d446d1b57083d9e6b75011'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'outcome'
                            value: 'success'
                            language: 'en'
                            dependent_value: 'NULL'
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: '6bc31367b1ff40618c9d05dc80017255'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'http_status'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '6eee70a1b402486abe1ab884ac5ae15c'
                        key: {
                            sys_security_acl: 'f3d2d3dc00d3429dbb0e7067a3e7d758'
                            sys_user_role: {
                                id: 'fe9449ee616b4ef6ae88a90c1443ce9e'
                                key: {
                                    name: 'x_1320561_itsm_ai.auditor'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '6f9950a9438e4fe08f7a4ba99683c04a'
                        key: {
                            name: 'x_1320561_itsm_ai.access_admin'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: '6fd30e44426645c2b340b998776b97e0'
                        key: {
                            role: {
                                id: '6f9950a9438e4fe08f7a4ba99683c04a'
                                key: {
                                    name: 'x_1320561_itsm_ai.access_admin'
                                }
                            }
                            contains: {
                                id: 'fe9449ee616b4ef6ae88a90c1443ce9e'
                                key: {
                                    name: 'x_1320561_itsm_ai.auditor'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '728d5bfc8f684648935d01f9c0792653'
                        key: {
                            name: 'x_1320561_itsm_ai.incident_write'
                        }
                    },
                    {
                        table: 'sys_ux_lib_asset'
                        id: '745a96717f134913b11ce670d07906d3'
                        key: {
                            name: 'x_1320561_itsm_ai/Textarea'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '74cb8eaee8284fb3af907dcdf7e4e81b'
                        deleted: true
                        key: {
                            sys_security_acl: '671165bae2a741339382be1fc09ebe8c'
                            sys_user_role: {
                                id: 'fe9449ee616b4ef6ae88a90c1443ce9e'
                                key: {
                                    name: 'x_1320561_itsm_ai.auditor'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '765e215971f643aca39830e152e06245'
                        key: {
                            sys_security_acl: '00bed5fed3394edb8d8944c94bfc0e4a'
                            sys_user_role: {
                                id: '5f3a0d576c8e45dfb39aa3eb3c8d5c61'
                                key: {
                                    name: 'x_1320561_itsm_ai.change_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: '7a7d2bb05bf1452d9ac8793ec23162f7'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'http_method'
                        }
                    },
                    {
                        table: 'sys_ux_lib_asset'
                        id: '83c00da9435e4fd6bf1e03c74230aea0'
                        key: {
                            name: 'x_1320561_itsm_ai/main.js.map'
                        }
                    },
                    {
                        table: 'sys_ui_page'
                        id: '85f006f1b84b426c812786a771e88a3e'
                        key: {
                            endpoint: 'x_1320561_itsm_ai_assistant.do'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '87bb281330cc4fd999d131415da64a6a'
                        key: {
                            sys_security_acl: 'b409448da240419cadac982f19811291'
                            sys_user_role: {
                                id: '698900b3a44f4b94ac8ca92da1bb5815'
                                key: {
                                    name: 'x_1320561_itsm_ai.catalog_write'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: '8840d5890306441f844fc6e5f72236eb'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'NULL'
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: '896680a5a1cf492da9fa53b9b79115a4'
                        key: {
                            name: 'x_1320561_itsm_ai.catalog_read'
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: '8e7062d9b3f149efa501a95468418aae'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'resource'
                        }
                    },
                    {
                        table: 'sys_ui_page'
                        id: '8ed1a37dd75a4d23838f1958a5ddb9f1'
                        key: {
                            endpoint: 'x_1320561_itsm_ai_agent_console.do'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '8f447d707209455c8a0b8e007d6aa982'
                        key: {
                            sys_security_acl: '3044da0804d64a65965fe7ffa0c3f94a'
                            sys_user_role: {
                                id: 'bacc16fe07ec4357b6fc201f2f4c277a'
                                key: {
                                    name: 'admin'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: '95e17ae6eed9447ea42c6efbcbe71c90'
                        key: {
                            sys_security_acl: '922b937642514902aeb3e9a74c999867'
                            sys_user_role: {
                                id: '896680a5a1cf492da9fa53b9b79115a4'
                                key: {
                                    name: 'x_1320561_itsm_ai.catalog_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: '9baf2ddc94bd421ca7f181a2e67c7941'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'session_id'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: '9f8db43e6eb04d32b33b67ec0785a9a9'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'record_number'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: 'a2bebc7870c646eb8dcb361eaac5c98f'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'user'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: 'a2e948a3415b46e39b1470045425cd81'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'NULL'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: 'a31f84c53ee04f81b7d6f8db51439e2f'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: '1140dffad8cf453d9732bb2fb11a5822'
                                key: {
                                    name: 'x_1320561_itsm_ai.incident_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: 'ab1ed61957fb4e5b9ac31e1f0b36b3cc'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'resource'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: 'ab288aae732348cdb5bbe86e3943b3e5'
                        key: {
                            sys_security_acl: '9a8aa2bc7c4b44bdb3de43b9052fd9a2'
                            sys_user_role: {
                                id: '1ae0999549f540e4bc81139e2cbab79b'
                                key: {
                                    name: 'x_1320561_itsm_ai.chat_user'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: 'ac9a5c99afb946b3886797894bb5fdb1'
                        key: {
                            name: 'x_1320561_itsm_ai.problem_write'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: 'afac923f06b4446e9776296cc32140c1'
                        key: {
                            sys_security_acl: '181791bfe67a435bb8301466ce09ad99'
                            sys_user_role: {
                                id: '728d5bfc8f684648935d01f9c0792653'
                                key: {
                                    name: 'x_1320561_itsm_ai.incident_write'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: 'b02185985e2342a2948a3f08e848829c'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'duration_ms'
                        }
                    },
                    {
                        table: 'sys_ux_lib_asset'
                        id: 'b2eea3091e2c464a83b1be15e4a197f7'
                        key: {
                            name: 'x_1320561_itsm_ai/assistant/main.js.map'
                        }
                    },
                    {
                        table: 'sys_choice'
                        id: 'b3484a50700f48e5ac174bfaac4a9dbb'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'outcome'
                            value: 'error'
                            language: 'en'
                            dependent_value: 'NULL'
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: 'b3839f0f1d3e4be8958bc8d2f1f1217c'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'record_number'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: 'b413c87a3da74dafba48e6ec36cb2b62'
                        key: {
                            sys_security_acl: '1774d36367f04a1bb8a8a1e355705c09'
                            sys_user_role: {
                                id: 'fe9449ee616b4ef6ae88a90c1443ce9e'
                                key: {
                                    name: 'x_1320561_itsm_ai.auditor'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: 'ba2c0cb5dd6d48d5a44a2dddd532bf0d'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'response'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: 'c1477b1725174738aa560f4c5ce1b930'
                        key: {
                            role: {
                                id: '728d5bfc8f684648935d01f9c0792653'
                                key: {
                                    name: 'x_1320561_itsm_ai.incident_write'
                                }
                            }
                            contains: {
                                id: '1140dffad8cf453d9732bb2fb11a5822'
                                key: {
                                    name: 'x_1320561_itsm_ai.incident_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: 'c4446c11c3a848fea4dedd41138bcd4b'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: '107e6baa14434c5b8c6189dde1702a84'
                                key: {
                                    name: 'x_1320561_itsm_ai.problem_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: 'c517fbb99daa48ec81ba1d3ceea6af20'
                        key: {
                            sys_security_acl: 'ce7ba3e5c61044d0abf38a936d3fe904'
                            sys_user_role: {
                                id: 'fe9449ee616b4ef6ae88a90c1443ce9e'
                                key: {
                                    name: 'x_1320561_itsm_ai.auditor'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: 'c6b4fa5cd03c4d29befca8c02c2c127f'
                        key: {
                            sys_security_acl: '0d5b84b7f57f4cbc92599814f273cc5f'
                            sys_user_role: {
                                id: '1140dffad8cf453d9732bb2fb11a5822'
                                key: {
                                    name: 'x_1320561_itsm_ai.incident_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: 'cc6a888b9e0045e89d012362f4346517'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: 'd7d2829ac537412a801230c948457929'
                                key: {
                                    name: 'x_1320561_itsm_ai.cmdb_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_choice_set'
                        id: 'cc8eb5db89294cd4a32baf5e9e4a1578'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'outcome'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: 'cdca0ef5146b4b2aae0fd870a17164a8'
                        key: {
                            sys_security_acl: 'bcedb7f1995a437f84d9bb4865943e1e'
                            sys_user_role: {
                                id: '107e6baa14434c5b8c6189dde1702a84'
                                key: {
                                    name: 'x_1320561_itsm_ai.problem_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: 'ce9f51191b4f4ce78b7a4ae4ba96be34'
                        deleted: true
                        key: {
                            sys_security_acl: 'd09f71530985480991b1dc528361d30b'
                            sys_user_role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: 'd0e2ecb13a514b69ad60ce7a8ae294df'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'outcome'
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: 'd1270c74601340068a56c43f8c0808f2'
                        key: {
                            sys_security_acl: '201036708a3649ee819b6681920aa9dc'
                            sys_user_role: {
                                id: '205ba15032c4405594ddaf28edfc38f6'
                                key: {
                                    name: 'x_1320561_itsm_ai.change_write'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_security_acl_role'
                        id: 'd33c3b26550f4c4f84df14b849b64457'
                        key: {
                            sys_security_acl: '4be53d126777402486e970f5e4ffafa1'
                            sys_user_role: {
                                id: 'fe9449ee616b4ef6ae88a90c1443ce9e'
                                key: {
                                    name: 'x_1320561_itsm_ai.auditor'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: 'd7d2829ac537412a801230c948457929'
                        key: {
                            name: 'x_1320561_itsm_ai.cmdb_read'
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: 'de3781a7e0ed439ba33bb5aa45e51baf'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'api'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: 'e1ab52b7448c4437a8c3563ea15abc3a'
                        key: {
                            role: {
                                id: '698900b3a44f4b94ac8ca92da1bb5815'
                                key: {
                                    name: 'x_1320561_itsm_ai.catalog_write'
                                }
                            }
                            contains: {
                                id: '896680a5a1cf492da9fa53b9b79115a4'
                                key: {
                                    name: 'x_1320561_itsm_ai.catalog_read'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_ux_lib_asset'
                        id: 'e39d386b129e4ff6b25ac2f1fef80ba2'
                        key: {
                            name: 'x_1320561_itsm_ai/main'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: 'eac0e1ba160d4341986c3ccbd558fdd8'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'api'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: 'ec326ba75760406cb266e0b291df9abb'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'outcome'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_ux_lib_asset'
                        id: 'ec957408fbcc4157a65ae25d4308169e'
                        key: {
                            name: 'x_1320561_itsm_ai/assistant/main'
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: 'ecd28187e887437780bbcff74979f7e6'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'response'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: 'f307814f9b094d9d826391e63e7362dd'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'record_sys_id'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_choice'
                        id: 'f51d7bfae8a74d5ebb5ace6adc4d62fb'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'outcome'
                            value: 'replay'
                            language: 'en'
                            dependent_value: 'NULL'
                        }
                    },
                    {
                        table: 'sys_documentation'
                        id: 'f611c6b4c03c426b852d608ceeec0378'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'http_method'
                            language: 'en'
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: 'f8c75ac9dd3b4bf8a7bd37df5b67d1cc'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: '1ae0999549f540e4bc81139e2cbab79b'
                                key: {
                                    name: 'x_1320561_itsm_ai.chat_user'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_user_role_contains'
                        id: 'fa955847e2f84172887eee6b1e4dab53'
                        key: {
                            role: {
                                id: '21914170032d4bd581b2e7aeb10b1563'
                                key: {
                                    name: 'x_1320561_itsm_ai.agent_user'
                                }
                            }
                            contains: {
                                id: 'ac9a5c99afb946b3886797894bb5fdb1'
                                key: {
                                    name: 'x_1320561_itsm_ai.problem_write'
                                }
                            }
                        }
                    },
                    {
                        table: 'sys_dictionary'
                        id: 'fe780e97307a47bd9ee26aba9dbec3e1'
                        key: {
                            name: 'x_1320561_itsm_ai_tool_audit'
                            element: 'idempotency_key'
                        }
                    },
                    {
                        table: 'sys_user_role'
                        id: 'fe9449ee616b4ef6ae88a90c1443ce9e'
                        key: {
                            name: 'x_1320561_itsm_ai.auditor'
                        }
                    },
                ]
            }
        }
    }
}
