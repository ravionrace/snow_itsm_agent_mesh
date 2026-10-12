import React from 'react'
import { HighlightedValue } from '@servicenow/react-components/HighlightedValue'
import { ButtonIconic } from '@servicenow/react-components/ButtonIconic'
import type { GroupRef, Level } from '../../store/features/access'

interface Props {
    group: GroupRef
    level: Level
    onRemove?: () => void
}

export default function GroupChip({ group, level, onRemove }: Props) {
    return (
        <span className="chip">
            <HighlightedValue label={`${group.name}${group.members !== undefined ? ` · ${group.members}` : ''}`}
                              color={level === 'write' ? 'orange' : 'blue'} variant="tertiary"
                              tooltipContent={`${group.members ?? 0} member(s)`} />
            {onRemove && (
                <ButtonIconic icon="close-outline" size="sm" bare tooltipContent={`Revoke ${level} from ${group.name}`}
                              onClicked={onRemove} />
            )}
        </span>
    )
}
