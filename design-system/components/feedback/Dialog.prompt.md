Modal for confirmations before sensitive actions (create draft order, send quotation) and short forms.

```jsx
<Dialog tone="warning" title="Tạo đơn hàng nháp?" description="Đơn nháp sẽ được ghi vào ERP và cần người có thẩm quyền duyệt." footer={<><Button variant="secondary">Hủy</Button><Button loading={busy}>Xác nhận tạo</Button></>} />
```

Keyboard: focus moves into the dialog, Tab is trapped, Esc closes (when onClose is set), focus returns to the trigger. danger/warning tones use role="alertdialog". Confirm button must use `loading` to block duplicate submits. Label the action verb explicitly — never "OK".
