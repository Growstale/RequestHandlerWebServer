import React, { memo } from 'react';
import { Label } from '@/components/ui/label';

const MemoizedShopSection = memo(function ShopSection({ shopName, chats, selectedChatIds, onSelectChat }) {
    return (
        <div>
            <h4 className="font-semibold text-sm mb-2 sticky top-0 bg-white py-1 z-10 border-b">{shopName}</h4>
            <div className="space-y-1 pl-2 border-l-2 mb-4">
                {chats.map(chat => (
                    <MemoizedChatItem
                        key={chat.shopContractorChatID}
                        chat={chat}
                        isSelected={selectedChatIds.has(chat.shopContractorChatID)}
                        onSelectChat={onSelectChat}
                    />
                ))}
            </div>
        </div>
    );
});

const MemoizedChatItem = memo(function ChatItem({ chat, isSelected, onSelectChat }) {
    return (
        <div className="flex items-center space-x-2 hover:bg-gray-50 p-1.5 rounded cursor-pointer transition-colors">
            <input
                type="checkbox"
                id={`chat-${chat.shopContractorChatID}`}
                checked={isSelected}
                onChange={(e) => onSelectChat(chat.shopContractorChatID, e.target.checked)}
                className="cursor-pointer mt-0.5"
            />
            <Label 
                htmlFor={`chat-${chat.shopContractorChatID}`} 
                className="font-normal cursor-pointer flex-grow text-sm"
            >
                {chat.contractorLogin || 'Без подрядчика'} 
                
                <span className="text-gray-400 text-xs ml-2">
                    (ID: {chat.telegramID}) 
                </span>
            </Label>
        </div>
    );
});

export default function RecipientSelector({ allChats, groupedChats, selectedChatIds, onSelectChat, onSelectAll, loading }) {
    return (
        <div className="border rounded-lg p-4 space-y-4 flex flex-col h-full bg-white shadow-sm">
            <div className="flex items-center justify-between pb-2 border-b">
                <h3 className="text-lg font-semibold">Получатели <span className="text-destructive">*</span></h3>
                <span className="text-xs text-muted-foreground">
                    Выбрано: {selectedChatIds.size}
                </span>
            </div>
            
            <div className="flex items-center space-x-2 bg-gray-50 p-2 rounded">
                <input
                    type="checkbox"
                    id="select-all-chats"
                    checked={allChats.length > 0 && selectedChatIds.size === allChats.length}
                    onChange={e => onSelectAll(e.target.checked)}
                    className="cursor-pointer"
                />
                <Label htmlFor="select-all-chats" className="cursor-pointer font-medium">
                    Выбрать все ({allChats.length})
                </Label>
            </div>

            <div className="flex-grow overflow-y-auto space-y-2 pr-2 custom-scrollbar">
                {loading && <p className="text-sm text-muted-foreground text-center py-4">Загрузка чатов...</p>}
                
                {!loading && allChats.length === 0 && (
                    <p className="text-sm text-muted-foreground text-center py-4">Чаты не найдены</p>
                )}

                {Object.entries(groupedChats).map(([shopName, chats]) => (
                    <MemoizedShopSection
                        key={shopName}
                        shopName={shopName}
                        chats={chats}
                        selectedChatIds={selectedChatIds}
                        onSelectChat={onSelectChat}
                    />
                ))}
            </div>
        </div>
    );
}