import React from 'react';
import { render, screen } from '@testing-library/react';
import SlackMessageRenderer from '../SlackMessageRenderer';

describe('SlackMessageRenderer', () => {
    describe('Basic Text', () => {
        it('renders plain text', () => {
            render(<SlackMessageRenderer text="Hello world" />);
            expect(screen.getByText('Hello world')).toBeInTheDocument();
        });

        it('renders empty text gracefully', () => {
            const { container } = render(<SlackMessageRenderer text="" />);
            expect(container.textContent).toBe('');
        });

        it('handles line breaks', () => {
            render(<SlackMessageRenderer text="Line 1\nLine 2\nLine 3" />);
            expect(screen.getByText(/Line 1/)).toBeInTheDocument();
            expect(screen.getByText(/Line 2/)).toBeInTheDocument();
            expect(screen.getByText(/Line 3/)).toBeInTheDocument();
        });
    });

    describe('Inline Code', () => {
        it('renders inline code with backticks', () => {
            render(<SlackMessageRenderer text="Use the `app` command" />);
            expect(screen.getByText(/Use the/)).toBeInTheDocument();
            expect(screen.getByText('app')).toBeInTheDocument();
            expect(screen.getByText(/command/)).toBeInTheDocument();
            
            const codeElement = screen.getByText('app');
            expect(codeElement.tagName).toBe('CODE');
        });

        it('renders multiple inline code segments', () => {
            render(<SlackMessageRenderer text="Run `npm install` then `npm start`" />);
            expect(screen.getByText('npm install')).toBeInTheDocument();
            expect(screen.getByText('npm start')).toBeInTheDocument();
        });

        it('handles inline code with special characters', () => {
            render(<SlackMessageRenderer text="Error: `Cannot find module 'react'`" />);
            expect(screen.getByText("Cannot find module 'react'")).toBeInTheDocument();
        });
    });

    describe('Code Blocks', () => {
        it('renders code blocks with triple backticks', () => {
            const codeText = 'function hello() {\n  return "world"\n}';
            render(<SlackMessageRenderer text={`\`\`\`${codeText}\`\`\``} />);
            
            const codeBlock = screen.getByText(/function hello/);
            expect(codeBlock.closest('code')).toBeInTheDocument();
            expect(codeBlock.closest('pre')).toBeInTheDocument();
        });

        it('renders code block with mixed content', () => {
            render(<SlackMessageRenderer text="Here is the error:\n```error log\nline 2```\nPlease fix." />);
            
            expect(screen.getByText(/Here is the error/)).toBeInTheDocument();
            expect(screen.getByText(/error log/)).toBeInTheDocument();
            expect(screen.getByText(/Please fix/)).toBeInTheDocument();
        });

        it('handles empty code blocks', () => {
            render(<SlackMessageRenderer text="Empty: ``````" />);
            expect(screen.getByText('Empty:')).toBeInTheDocument();
        });
    });

    describe('Mixed Formatting', () => {
        it('renders code blocks and inline code together', () => {
            const text = 'Use `npm install` to install:\n```npm install react```';
            render(<SlackMessageRenderer text={text} />);
            
            expect(screen.getByText('npm install')).toBeInTheDocument();
            expect(screen.getByText('npm install react')).toBeInTheDocument();
        });

        it('renders inline code after code block', () => {
            render(<SlackMessageRenderer text="```block```\nThen use `inline`" />);
            expect(screen.getByText('block')).toBeInTheDocument();
            expect(screen.getByText('inline')).toBeInTheDocument();
        });

        it('preserves text with mentions (already resolved by backend)', () => {
            render(<SlackMessageRenderer text="Hello @support-team! Please run `npm start`" />);
            expect(screen.getByText(/Hello @support-team!/)).toBeInTheDocument();
            expect(screen.getByText('npm start')).toBeInTheDocument();
        });
    });

    describe('Edge Cases', () => {
        it('handles nested backticks safely', () => {
            // Single backticks inside text (not valid inline code)
            render(<SlackMessageRenderer text="This ` is not ` valid code" />);
            expect(screen.getByText(/This/)).toBeInTheDocument();
        });

        it('handles unclosed backticks', () => {
            render(<SlackMessageRenderer text="Unclosed `code block" />);
            // Should render as-is without crashing
            expect(screen.getByText(/Unclosed/)).toBeInTheDocument();
        });

        it('handles special characters in text', () => {
            render(<SlackMessageRenderer text="Special: <>&" />);
            expect(screen.getByText(/Special:/)).toBeInTheDocument();
        });

        it('applies custom className', () => {
            const { container } = render(<SlackMessageRenderer text="Test" className="custom-class" />);
            const div = container.querySelector('.custom-class');
            expect(div).toBeInTheDocument();
        });
    });

    describe('Real-World Examples', () => {
        it('renders typical support ticket message', () => {
            const message = `Hello team! My application called \`app\` had some issues yesterday:

\`\`\`
Error: Cannot connect to database
  at Connection.connect (db.js:45)
  at Server.start (server.js:12)
\`\`\`

Can you please help me?`;

            render(<SlackMessageRenderer text={message} />);
            
            expect(screen.getByText('Hello team! My application called')).toBeInTheDocument();
            expect(screen.getByText('app')).toBeInTheDocument();
            expect(screen.getByText(/Error: Cannot connect to database/)).toBeInTheDocument();
            expect(screen.getByText('Can you please help me?')).toBeInTheDocument();
        });

        it('renders message with backend-resolved mentions', () => {
            // Backend would have already replaced <!subteam^S08948NBMED> with @support-team
            const message = `Hello @support-team! Please check \`server.log\` for errors.`;
            
            render(<SlackMessageRenderer text={message} />);
            
            expect(screen.getByText(/Hello @support-team!/)).toBeInTheDocument();
            expect(screen.getByText('server.log')).toBeInTheDocument();
        });
    });
});

