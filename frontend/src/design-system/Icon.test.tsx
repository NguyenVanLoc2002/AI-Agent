import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Icon } from '.';

// A-66: the design-system original put `title` into innerHTML unescaped.
describe('Icon', () => {
  it('renders the title as text, so markup in it never becomes an element', () => {
    const { container } = render(<Icon name="search" title={'</title><img src=x onerror="alert(1)"><title>'} />);

    const svg = container.querySelector('svg');
    expect(svg?.querySelector('img')).toBeNull();
    expect(svg?.querySelector('title')?.textContent).toBe('</title><img src=x onerror="alert(1)"><title>');
    expect(svg).toHaveAttribute('role', 'img');
  });

  it('draws a known icon and hides it from assistive technology without a title', () => {
    const { container } = render(<Icon name="search" />);

    const svg = container.querySelector('svg');
    expect(svg?.querySelectorAll('path, circle').length).toBeGreaterThan(0);
    expect(svg).toHaveAttribute('aria-hidden', 'true');
    expect(svg?.querySelector('title')).toBeNull();
  });

  it.each(['constructor', '__proto__', 'toString', 'no-such-icon'])('renders nothing for "%s"', (name) => {
    const { container } = render(<Icon name={name} />);

    expect(container.innerHTML).toBe('');
  });
});
