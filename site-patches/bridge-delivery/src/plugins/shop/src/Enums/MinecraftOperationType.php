<?php

namespace Azuriom\Plugin\Shop\Enums;

/**
 * Merge these cases into the production enum (do not replace unknown extra cases).
 */
enum MinecraftOperationType: string
{
    case PrivilegeReconcile = 'privilege_reconcile';
    case PrivilegeRevoke = 'privilege_revoke';
    case KitRedeem = 'kit_redeem';
    case GroupGrant = 'group_grant';
    case GroupRevoke = 'group_revoke';
    case KitCredit = 'kit_credit';
}
