import { Star } from "lucide-react";

export function StarRating({ rating, hideValue = false }: { rating: number; hideValue?: boolean }) {
  const max = 5;
  const fullStars = Math.floor(rating);
  const halfStar = rating % 1 >= 0.5;
  return (
    <div className="inline-flex items-center gap-0.5">
      {Array.from({ length: max }, (_, i) => {
        if (i < fullStars) {
          return <Star key={i} className="fill-warning text-warning h-4 w-4" />;
        }
        if (i === fullStars && halfStar) {
          return <Star key={i} className="text-warning h-4 w-4" />;
        }
        return <Star key={i} className="text-muted-foreground/40 h-4 w-4" />;
      })}
      {!hideValue && <span className="text-muted-foreground ml-1 text-sm">{rating.toFixed(1)}</span>}
    </div>
  );
}
