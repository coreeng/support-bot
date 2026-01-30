'use client'

import { createContext, useContext, ReactNode, useState, useEffect } from 'react'

type IframeContextType = {
  isIframe: boolean
}

const IframeContext = createContext<IframeContextType | undefined>(undefined)

export const IframeProvider = ({ children }: { children: ReactNode }) => {
  const [isIframe, setIsIframe] = useState(false)

  useEffect(() => {
    // Detect if we're running in an iframe
    const inIframe = (() => {
      try {
        return window.self !== window.top
      } catch {
        return true // Assume iframe if access is blocked
      }
    })()

    setIsIframe(inIframe)
  }, [])

  return (
    <IframeContext.Provider value={{ isIframe }}>
      {children}
    </IframeContext.Provider>
  )
}

export const useIframe = () => {
  const context = useContext(IframeContext)
  if (context === undefined) {
    throw new Error('useIframe must be used within an IframeProvider')
  }
  return context
}
