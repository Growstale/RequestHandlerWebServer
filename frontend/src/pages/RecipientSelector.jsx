import React, { memo } from 'react';
import { Label } from '@/components/ui/label';

// Оборачиваем компоненты в React.memo для предотвращения лишних перерисовок
const MemoizedShopSection = memo(function ShopSection({ shopName, chats, selectedChatIds, onSelectChat }) {
    return (
        <div>
            <h4 className="font-semibold text-sm mb-2 sticky top-0 bg-white py-1">{shopName}</h4>
            <div className="space-y-2 pl-2 border-l-2">
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
        <div className="flex items-center space-x-2">
            <input
                type="checkbox"
                id={`chat-${chat.shopContractorChatID}`}
                checked={isSelected}
                onChange={(e) => onSelectChat(chat.shopContractorChatID, e.target.checked)}
            />
            <Label htmlFor={`chat-${chat.shopContractorChatID}`} className="font-normal cursor-pointer">
                {chat.contractorLogin}
            </Label>
        </div>
    );
});

// Основной компонент, который мы будем использовать
export default function RecipientSelector({ allChats, groupedChats, selectedChatIds, onSelectChat, onSelectAll, loading }) {
    return (
        <div className="border rounded-lg p-4 space-y-4 flex flex-col h-full">
            <h3 className="text-lg font-semibold">Получатели <span className="text-destructive">*</span></h3>
            <div className="flex items-center space-x-2">
                <input
                    type="checkbox"
                    id="select-all-chats"
                    checked={allChats.length > 0 && selectedChatIds.size === allChats.length}
                    onChange={e => onSelectAll(e.target.checked)}
                />
                <Label htmlFor="select-all-chats" className="cursor-pointer">Выбрать все</Label>
            </div>
            <div className="flex-grow overflow-y-auto space-y-4 pr-2">
                {loading && <p>Загрузка чатов...</p>}
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