// src/components/dashboards/RefreshButton.tsx
import { RefreshCw } from 'lucide-react'

interface RefreshButtonProps {
    onRefresh: () => void
    isRefreshing: boolean
}

export function RefreshButton({ onRefresh, isRefreshing }: RefreshButtonProps) {
    return (
        <div className="mb-4 flex justify-end">
            <button
                onClick={onRefresh}
                disabled={isRefreshing}
                className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-all ${
                    isRefreshing 
                        ? 'bg-gray-200 text-gray-500 cursor-not-allowed' 
                        : 'bg-blue-100 text-blue-700 hover:bg-blue-200 border border-blue-300'
                }`}
                title="Refresh data"
            >
                <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`} />
                <span>{isRefreshing ? 'Refreshing...' : 'Refresh'}</span>
            </button>
        </div>
    )
}

