import React, { useState } from 'react';
import { Icon } from '../core/Icon.jsx';

// APAS change (EP-10b, A-68): `label` names the text box for assistive technology (the original had a
// placeholder only), `maxLength` is passed to it, and Enter does not send while an input method is still
// composing a character (Vietnamese and other IMEs).
export function AIComposer({ value, onChange, onSubmit, placeholder = 'Hỏi về sản phẩm, giá, tồn kho…', context = [], disabled = false, busy = false, suggestions = [], onSuggestion, disclaimer = 'AI có thể sai. Giá và tồn kho chỉ lấy từ thẻ dữ liệu, kèm thời điểm tra cứu do hệ thống ghi.', label = 'Nội dung tin nhắn', maxLength, style }) {
  const [inner, setInner] = useState('');
  const v = value ?? inner;
  const set = onChange ?? setInner;
  const [focus, setFocus] = useState(false);
  const canSend = v.trim().length > 0 && !disabled && !busy;
  const send = () => { if (!canSend) return; onSubmit && onSubmit(v.trim()); if (value === undefined) setInner(''); };
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, ...style }}>
      {suggestions.length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {suggestions.map((s) => (
            <button key={s} type="button" disabled={disabled} onClick={() => onSuggestion ? onSuggestion(s) : set(s)}
              style={{ height: 26, padding: '0 10px', borderRadius: 999, border: '1px solid var(--border-default)', background: 'var(--bg-surface)', font: '500 12px/1.4 var(--font-sans)', color: 'var(--text-body)', cursor: 'pointer' }}>{s}</button>
          ))}
        </div>
      )}
      <div style={{ border: `1px solid ${focus ? 'var(--border-focus)' : 'var(--border-default)'}`, boxShadow: focus ? 'var(--focus-ring)' : 'var(--shadow-xs)', borderRadius: 'var(--radius-lg)', background: disabled ? 'var(--bg-muted)' : 'var(--bg-surface)', padding: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
        {context.length > 0 && (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {context.map((c) => (
              <span key={c} style={{ display: 'inline-flex', alignItems: 'center', gap: 4, height: 22, padding: '0 8px', borderRadius: 6, background: 'var(--bg-muted)', font: '500 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>
                <Icon name="paperclip" size={14} />{c}
              </span>
            ))}
          </div>
        )}
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
          <textarea rows={2} value={v} disabled={disabled} aria-label={label} maxLength={maxLength} placeholder={disabled ? 'Trợ lý AI tạm thời không khả dụng' : placeholder}
            onChange={(e) => set(e.target.value)} onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); send(); } }}
            style={{ flex: 1, resize: 'none', border: 0, outline: 'none', background: 'transparent', font: '400 14px/1.5 var(--font-sans)', color: 'var(--text-primary)', padding: '2px 4px' }} />
          <button type="button" aria-label="Gửi" onClick={send} disabled={!canSend}
            style={{ width: 32, height: 32, borderRadius: 8, border: 0, background: canSend ? 'var(--action-primary)' : 'var(--gray-200)', color: canSend ? 'var(--text-on-primary)' : 'var(--text-tertiary)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', cursor: canSend ? 'pointer' : 'not-allowed' }}>
            <Icon name={busy ? 'loader-circle' : 'send'} size={16} style={{ animation: busy ? 'ap-spin 0.9s linear infinite' : undefined }} />
          </button>
        </div>
      </div>
      {disclaimer ? <div style={{ font: '400 12px/1.45 var(--font-sans)', color: 'var(--text-secondary)', textAlign: 'center' }}>{disclaimer}</div> : null}
    </div>
  );
}
