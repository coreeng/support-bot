'use client'

import React from 'react'

interface SlackMessageRendererProps {
    text: string
    className?: string
}

/**
 * Renders Slack-formatted messages with proper code blocks and inline code.
 * Backend should resolve mentions before sending text to this component.
 */
export const SlackMessageRenderer: React.FC<SlackMessageRendererProps> = ({ text, className = '' }) => {
    const parseMessage = (rawText: string): React.ReactNode[] => {
        const elements: React.ReactNode[] = []

        // First, extract code blocks (```...```) to protect them from inline processing
        const codeBlockRegex = /```([\s\S]*?)```/g
        const segments: Array<{ type: 'text' | 'codeblock'; content: string }> = []
        
        let lastIndex = 0
        let match: RegExpExecArray | null

        while ((match = codeBlockRegex.exec(rawText)) !== null) {
            // Add text before code block
            if (match.index > lastIndex) {
                segments.push({ type: 'text', content: rawText.substring(lastIndex, match.index) })
            }
            // Add code block
            segments.push({ type: 'codeblock', content: match[1] })
            lastIndex = match.index + match[0].length
        }
        // Add remaining text
        if (lastIndex < rawText.length) {
            segments.push({ type: 'text', content: rawText.substring(lastIndex) })
        }

        // Process each segment
        segments.forEach((segment, segmentIdx) => {
            if (segment.type === 'codeblock') {
                // Render code block
                elements.push(
                    <pre 
                        key={`codeblock-${segmentIdx}`}
                        className="bg-gray-900 text-gray-100 p-4 rounded-md overflow-x-auto font-mono text-sm my-2 border border-gray-700"
                    >
                        <code>{segment.content}</code>
                    </pre>
                )
            } else {
                // Process text segment for inline code and line breaks
                const textParts = parseInlineFormatting(segment.content, segmentIdx * 1000)
                elements.push(...textParts)
            }
        })

        return elements
    }

    const parseInlineFormatting = (text: string, baseKey: number): React.ReactNode[] => {
        const elements: React.ReactNode[] = []
        const inlineCodeRegex = /`([^`]+)`/g
        
        let lastIndex = 0
        let match: RegExpExecArray | null
        let keyCounter = baseKey

        while ((match = inlineCodeRegex.exec(text)) !== null) {
            // Add text before inline code
            if (match.index > lastIndex) {
                const beforeText = text.substring(lastIndex, match.index)
                elements.push(...parseLineBreaks(beforeText, keyCounter++))
            }
            
            // Add inline code
            elements.push(
                <code 
                    key={`inline-${keyCounter++}`}
                    className="bg-gray-100 text-gray-800 px-1.5 py-0.5 rounded font-mono text-sm border border-gray-200"
                >
                    {match[1]}
                </code>
            )
            lastIndex = match.index + match[0].length
        }
        
        // Add remaining text
        if (lastIndex < text.length) {
            const remainingText = text.substring(lastIndex)
            elements.push(...parseLineBreaks(remainingText, keyCounter))
        }

        return elements
    }

    const parseLineBreaks = (text: string, baseKey: number): React.ReactNode[] => {
        if (!text.includes('\n')) {
            return [text]
        }

        const lines = text.split('\n')
        const elements: React.ReactNode[] = []
        
        lines.forEach((line, idx) => {
            elements.push(<span key={`line-${baseKey}-${idx}`}>{line}</span>)
            if (idx < lines.length - 1) {
                elements.push(<br key={`br-${baseKey}-${idx}`} />)
            }
        })
        
        return elements
    }

    if (!text) {
        return null
    }

    const renderedContent = parseMessage(text)

    return (
        <div className={`text-gray-700 leading-relaxed ${className}`}>
            {renderedContent}
        </div>
    )
}

export default SlackMessageRenderer

