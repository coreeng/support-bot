// src/components/dashboards/RefreshButton.tsx
import { RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface RefreshButtonProps {
 onRefresh: () => void
 isRefreshing: boolean
}

export function RefreshButton({ onRefresh, isRefreshing }: RefreshButtonProps) {
 return (
 <Button
 variant="outline"
 size="sm"
 onClick={onRefresh}
 disabled={isRefreshing}
 className="cursor-pointer"
 title="Refresh data"
 >
 <RefreshCw className={`h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
 {isRefreshing ? 'Refreshing...' : 'Refresh'}
 </Button>
 )
}
