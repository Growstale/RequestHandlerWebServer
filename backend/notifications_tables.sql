-- SQL скрипт для создания таблиц уведомлений
-- Выполните этот скрипт в вашей базе данных, если таблицы еще не созданы

USE [YourDatabaseName]; -- Замените на имя вашей базы данных
GO

-- Создание таблицы уведомлений
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Notifications' AND xtype='U')
BEGIN
    CREATE TABLE dbo.Notifications (
        NotificationID INT IDENTITY(1,1) NOT NULL,
        Title NVARCHAR(200) NOT NULL,
        Message NVARCHAR(MAX) NULL,
        ImageData VARBINARY(MAX) NULL,
        CronExpression NVARCHAR(100) NOT NULL,
        IsActive BIT NOT NULL DEFAULT 1,
        CONSTRAINT PK_Notifications PRIMARY KEY (NotificationID),
        CONSTRAINT CK_Notifications_CronFormat CHECK (
            CronExpression LIKE '% % % % %'
        )
    );
    PRINT 'Таблица Notifications создана успешно';
END
ELSE
BEGIN
    PRINT 'Таблица Notifications уже существует';
END
GO

-- Создание таблицы получателей уведомлений
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='NotificationRecipients' AND xtype='U')
BEGIN
    CREATE TABLE dbo.NotificationRecipients (
        NotificationRecipientID INT IDENTITY(1,1) NOT NULL,
        NotificationID INT NOT NULL,
        ShopContractorChatID INT NOT NULL,
        CONSTRAINT PK_NotificationRecipients PRIMARY KEY (NotificationRecipientID),
        CONSTRAINT FK_NotificationRecipients_Notifications FOREIGN KEY (NotificationID) 
            REFERENCES dbo.Notifications(NotificationID) ON DELETE CASCADE,
        CONSTRAINT FK_NotificationRecipients_ShopContractorChats FOREIGN KEY (ShopContractorChatID) 
            REFERENCES dbo.ShopContractorChats(ShopContractorChatID) ON DELETE CASCADE
    );
    PRINT 'Таблица NotificationRecipients создана успешно';
END
ELSE
BEGIN
    PRINT 'Таблица NotificationRecipients уже существует';
END
GO

-- Создание индексов для оптимизации производительности
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_NotificationRecipients_NotificationID')
BEGIN
    CREATE INDEX IX_NotificationRecipients_NotificationID ON dbo.NotificationRecipients(NotificationID);
    PRINT 'Индекс IX_NotificationRecipients_NotificationID создан';
END

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_Notifications_IsActive')
BEGIN
    CREATE INDEX IX_Notifications_IsActive ON dbo.Notifications(IsActive);
    PRINT 'Индекс IX_Notifications_IsActive создан';
END

PRINT 'Скрипт создания таблиц уведомлений выполнен успешно!';
