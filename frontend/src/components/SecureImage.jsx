// src/components/SecureImage.jsx

import React, { useState, useEffect, useRef } from 'react';
import { getPhotoBlob } from '@/api/requestApi';
import { RefreshCw, AlertTriangle, Image as ImageIcon } from 'lucide-react';

export default function SecureImage({ photoId, className, alt }) {
    const [imageSrc, setImageSrc] = useState(null);
    const [loading, setLoading] = useState(false); // Загрузка начнется только при видимости
    const [error, setError] = useState(false);
    const [isVisible, setIsVisible] = useState(false);
    const placeholderRef = useRef(null);


    useEffect(() => {
        const observer = new IntersectionObserver(
            (entries) => {
                // Когда элемент появляется в области видимости
                if (entries[0].isIntersecting) {
                    setIsVisible(true);
                    // Отписываемся, чтобы не срабатывать повторно
                    observer.disconnect();
                }
            },
            { rootMargin: "100px" } // Начать загрузку за 100px до появления на экране
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
        if (!isVisible || imageSrc) return;

        let isMounted = true;
        let objectUrl = null;
        setLoading(true);

        const fetchImage = async () => {
            if (!photoId) {
                setLoading(false);
                setError(true);
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
    }, [isVisible, photoId, imageSrc]); // Добавляем isVisible в зависимости

    if (loading) {
        return (
            <div className={`flex items-center justify-center bg-gray-200 rounded-lg ${className}`}>
                <RefreshCw className="h-6 w-6 text-gray-500 animate-spin" />
            </div>
        );
    }

    if (error) {
        return (
             <div className={`flex items-center justify-center bg-red-100 rounded-lg ${className}`}>
                <AlertTriangle className="h-6 w-6 text-red-500" />
            </div>
        );
    }

    if (imageSrc) {
        return <img src={imageSrc} alt={alt || `Photo ${photoId}`} className={className} />;
    }

    // В остальных случаях рендерим "заглушку"
    return (
        <div ref={placeholderRef} className={`flex items-center justify-center bg-gray-200 rounded-lg ${className}`}>
            {loading && <RefreshCw className="h-6 w-6 text-gray-500 animate-spin" />}
            {error && <AlertTriangle className="h-6 w-6 text-red-500" />}
            {!loading && !error && <ImageIcon className="h-6 w-6 text-gray-400" />}
        </div>
    );

}