import { useState } from 'react';
import type { Handoff } from '../../api/staff';
import { StaffConversationPage } from './StaffConversationPage';
import { StaffInboxPage, type InboxNotice } from './StaffInboxPage';

// SALES workspace: F2 inbox and F3 held conversation (EP-10c). The open conversation lives in memory only:
// a page reload returns to the inbox, where the backend decides again what this member may open.

interface Props {
  onUnauthenticated: () => void;
}

export function StaffWorkspace({ onUnauthenticated }: Props) {
  const [open, setOpen] = useState<Handoff | null>(null);
  const [notice, setNotice] = useState<InboxNotice | null>(null);
  /** Back from a conversation: the inbox starts focus at its heading. */
  const [returned, setReturned] = useState(false);

  if (open) {
    return (
      <StaffConversationPage key={open.handoffId} handoff={open} onUnauthenticated={onUnauthenticated}
        onLeave={(next) => { setNotice(next); setReturned(true); setOpen(null); }} />
    );
  }
  return <StaffInboxPage focusHeading={returned} notice={notice} onNotice={setNotice} onOpen={(h) => { setNotice(null); setOpen(h); }} onUnauthenticated={onUnauthenticated} />;
}
