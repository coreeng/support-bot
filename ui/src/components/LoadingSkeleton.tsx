'use client'

export default function LoadingSkeleton() {
    return (
        <div className="p-6 space-y-6 animate-pulse">
            {/* Header skeleton */}
            <div className="h-8 bg-gradient-to-r from-gray-200 via-gray-300 to-gray-200 rounded-lg w-64 animate-shimmer bg-[length:200%_100%]"></div>
            
            {/* Cards skeleton */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                {[1, 2, 3, 4].map((i) => (
                    <div key={i} className="p-6 bg-white shadow-md rounded-lg border border-gray-200">
                        <div className="h-4 bg-gradient-to-r from-gray-200 via-gray-300 to-gray-200 rounded w-24 mb-3 animate-shimmer bg-[length:200%_100%]"></div>
                        <div className="h-8 bg-gradient-to-r from-gray-200 via-gray-300 to-gray-200 rounded w-16 animate-shimmer bg-[length:200%_100%]"></div>
                    </div>
                ))}
            </div>

            {/* Chart skeletons */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {[1, 2].map((i) => (
                    <div key={i} className="bg-white shadow-md rounded-lg p-6 border border-gray-200">
                        <div className="h-4 bg-gradient-to-r from-gray-200 via-gray-300 to-gray-200 rounded w-32 mb-4 animate-shimmer bg-[length:200%_100%]"></div>
                        <div className="h-64 bg-gradient-to-r from-gray-200 via-gray-300 to-gray-200 rounded animate-shimmer bg-[length:200%_100%]"></div>
                    </div>
                ))}
            </div>
        </div>
    )
}

