// src/lib/utils/__tests__/query-params.test.ts
import { buildDateQuery, extractDateParams } from '../query-params'

describe('buildDateQuery', () => {
    it('should build query string with both dates', () => {
        const result = buildDateQuery('2025-01-01', '2025-01-31')
        expect(result).toBe('?dateFrom=2025-01-01&dateTo=2025-01-31')
    })

    it('should return empty string when both dates are undefined', () => {
        const result = buildDateQuery(undefined, undefined)
        expect(result).toBe('')
    })

    it('should return empty string when both dates are empty strings', () => {
        const result = buildDateQuery('', '')
        expect(result).toBe('')
    })

    it('should include only dateFrom if dateTo is missing', () => {
        const result = buildDateQuery('2025-01-01', undefined)
        expect(result).toBe('?dateFrom=2025-01-01')
    })

    it('should include only dateTo if dateFrom is missing', () => {
        const result = buildDateQuery(undefined, '2025-01-31')
        expect(result).toBe('?dateTo=2025-01-31')
    })

    it('should URL encode date values', () => {
        const result = buildDateQuery('2025-01-01', '2025-01-31')
        expect(result).toContain('2025-01-01')
        expect(result).toContain('2025-01-31')
    })
})

describe('extractDateParams', () => {
    it('should extract both date parameters', () => {
        const mockRequest = { 
            url: 'http://localhost/api/db/dashboard?dateFrom=2025-01-01&dateTo=2025-01-31' 
        } as Request
        const result = extractDateParams(mockRequest)
        
        expect(result.dateFrom).toBe('2025-01-01')
        expect(result.dateTo).toBe('2025-01-31')
    })

    it('should return undefined for missing parameters', () => {
        const mockRequest = { 
            url: 'http://localhost/api/db/dashboard' 
        } as Request
        const result = extractDateParams(mockRequest)
        
        expect(result.dateFrom).toBeUndefined()
        expect(result.dateTo).toBeUndefined()
    })

    it('should extract only dateFrom if dateTo is missing', () => {
        const mockRequest = { 
            url: 'http://localhost/api/db/dashboard?dateFrom=2025-01-01' 
        } as Request
        const result = extractDateParams(mockRequest)
        
        expect(result.dateFrom).toBe('2025-01-01')
        expect(result.dateTo).toBeUndefined()
    })

    it('should extract only dateTo if dateFrom is missing', () => {
        const mockRequest = { 
            url: 'http://localhost/api/db/dashboard?dateTo=2025-01-31' 
        } as Request
        const result = extractDateParams(mockRequest)
        
        expect(result.dateFrom).toBeUndefined()
        expect(result.dateTo).toBe('2025-01-31')
    })

    it('should handle URL with other parameters', () => {
        const mockRequest = { 
            url: 'http://localhost/api/db/dashboard?foo=bar&dateFrom=2025-01-01&baz=qux&dateTo=2025-01-31' 
        } as Request
        const result = extractDateParams(mockRequest)
        
        expect(result.dateFrom).toBe('2025-01-01')
        expect(result.dateTo).toBe('2025-01-31')
    })
})

