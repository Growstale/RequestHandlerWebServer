import React, { useState, useEffect, useRef } from 'react';
import { getPhotoBlob } from '@/api/requestApi';
import { RefreshCw, AlertTriangle, Image as ImageIcon } from 'lucide-react';
import { cn } from '@/lib/utils';

export default function SecureImage({ photoId, className, alt, style }) {
    const [imageSrc, setImageSrc] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(false);
    const [isVisible, setIsVisible] = useState(false);
    const placeholderRef = useRef(null);

    useEffect(() => {
        const observer = new IntersectionObserver(
            (entries) => {
                if (entries[0].isIntersecting) {
                    setIsVisible(true);
                    observer.disconnect();
                }
            },
            { rootMargin: "100px" }
        );

        const currentRef = placeholderRef.current;
        if (currentRef) {
            observer.observe(currentRef);
        }

        return () => {
            if (currentRef) {
                observer.unobserve(currentRef);
            }
        };
    }, []);

    useEffect(() => {
        if (!isVisible) return;

        let isMounted = true;
        let objectUrl = null;
        
        setLoading(true);
        setError(false);
        
        const fetchImage = async () => {
            if (!photoId) {
                if (isMounted) {
                    setLoading(false);
                    setError(true);
                }
                return;
            }
            
            try {
                const response = await getPhotoBlob(photoId);
                objectUrl = URL.createObjectURL(response.data);
                if (isMounted) {
                    setImageSrc(objectUrl);
                }
            } catch (err) {
                console.error(`Failed to load secure image ${photoId}`, err);
                if (isMounted) {
                    setError(true);
                }
            } finally {
                if (isMounted) {
                    setLoading(false);
                }
            }
        };

        fetchImage();

        return () => {
            isMounted = false;
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
            }
        };
    }, [isVisible, photoId]);

    const placeholderClass = cn(
        "flex items-center justify-center bg-gray-100 rounded-lg text-gray-400 w-full h-full min-h-[100px]", 
        className
    );

    if (loading) {
        return (
            <div className={placeholderClass} ref={placeholderRef} style={style}>
                <RefreshCw className="h-8 w-8 animate-spin" />
            </div>
        );
    }

    if (error) {
        return (
             <div className={cn(placeholderClass, "bg-red-50 text-red-400")} style={style}>
                <AlertTriangle className="h-8 w-8" />
            </div>
        );
    }

    if (imageSrc) {
        return (
            <img 
                src={imageSrc} 
                alt={alt || `Photo ${photoId}`} 
                className={className} 
                style={style}
            />
        );
    }

    return (
        <div ref={placeholderRef} className={placeholderClass} style={style}>
            <ImageIcon className="h-8 w-8" />
        </div>
    );
}