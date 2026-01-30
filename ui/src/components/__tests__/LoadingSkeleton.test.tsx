import React from 'react';
import { render } from '@testing-library/react';
import LoadingSkeleton from '../LoadingSkeleton';

describe('LoadingSkeleton', () => {
    it('renders skeleton with pulse animation', () => {
        const { container } = render(<LoadingSkeleton />);
        
        const pulseElement = container.querySelector('.animate-pulse');
        expect(pulseElement).toBeInTheDocument();
    });

    it('renders skeleton header', () => {
        const { container } = render(<LoadingSkeleton />);
        
        // Should have a header skeleton (h-8)
        const headerSkeleton = container.querySelector('.h-8');
        expect(headerSkeleton).toBeInTheDocument();
    });

    it('renders 4 card skeletons', () => {
        const { container } = render(<LoadingSkeleton />);
        
        // Grid should have 4 cards
        const cards = container.querySelectorAll('.grid.grid-cols-1.md\\:grid-cols-2.lg\\:grid-cols-4 > div');
        expect(cards.length).toBe(4);
    });

    it('renders chart skeleton section', () => {
        const { container } = render(<LoadingSkeleton />);
        
        // Should have chart placeholders section (large rectangular skeletons)
        const largeSkeletons = container.querySelectorAll('.h-64');
        expect(largeSkeletons.length).toBeGreaterThanOrEqual(2);
    });

    it('applies shimmer animation to skeleton elements', () => {
        const { container } = render(<LoadingSkeleton />);
        
        const shimmerElements = container.querySelectorAll('.animate-shimmer');
        expect(shimmerElements.length).toBeGreaterThan(0);
    });
});

