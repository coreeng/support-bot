"use client";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuth } from "@/hooks/useAuth";
import { LogOut } from "lucide-react";

export function UserDropdown() {
  const { user, logout } = useAuth();
  if (!user) return null;

  const initial = user.email.charAt(0).toUpperCase();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="cursor-pointer">
          <Avatar className="h-full w-full rounded-lg">
            <AvatarFallback className="rounded-lg text-xs">{initial}</AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-max max-w-[min(32rem,calc(100vw-2rem))] min-w-[12rem]">
        <div className="flex items-center gap-2 p-2">
          <Avatar className="h-8 w-8 rounded-lg">
            <AvatarFallback className="rounded-lg">{initial}</AvatarFallback>
          </Avatar>
          <div className="flex flex-col space-y-1">
            <p className="text-sm leading-none font-medium break-words">{user.email}</p>
            {user.teams?.length ? (
              <p className="text-muted-foreground text-xs leading-none break-all">
                {user.teams.length} {user.teams.length === 1 ? "team" : "teams"}
              </p>
            ) : null}
          </div>
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <button type="button" onClick={() => logout()} className="w-full cursor-pointer">
            <LogOut className="mr-2 h-4 w-4" />
            Log out
          </button>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
