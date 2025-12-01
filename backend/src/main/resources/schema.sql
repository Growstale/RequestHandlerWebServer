-- Очистка индексов (если они существуют отдельно)
IF EXISTS (SELECT name FROM sys.indexes WHERE name = 'IX_Requests_ShopID' AND object_id = OBJECT_ID('dbo.Requests'))
    DROP INDEX IX_Requests_ShopID ON dbo.Requests;

IF EXISTS (SELECT name FROM sys.indexes WHERE name = 'IX_Requests_Status' AND object_id = OBJECT_ID('dbo.Requests'))
    DROP INDEX IX_Requests_Status ON dbo.Requests;

IF EXISTS (SELECT name FROM sys.indexes WHERE name = 'IX_Requests_WorkCategoryID' AND object_id = OBJECT_ID('dbo.Requests'))
    DROP INDEX IX_Requests_WorkCategoryID ON dbo.Requests;

IF EXISTS (SELECT name FROM sys.indexes WHERE name = 'IX_Requests_UrgencyID' AND object_id = OBJECT_ID('dbo.Requests'))
    DROP INDEX IX_Requests_UrgencyID ON dbo.Requests;

IF EXISTS (SELECT name FROM sys.indexes WHERE name = 'IX_RequestComments_RequestID' AND object_id = OBJECT_ID('dbo.RequestComments'))
    DROP INDEX IX_RequestComments_RequestID ON dbo.RequestComments;

IF EXISTS (SELECT name FROM sys.indexes WHERE name = 'UQ_Users_TelegramID_Filtered' AND object_id = OBJECT_ID('dbo.Users'))
    DROP INDEX UQ_Users_TelegramID_Filtered ON dbo.Users;

IF EXISTS (SELECT name FROM sys.indexes WHERE name = 'IX_RequestCustomDays_Requests' AND object_id = OBJECT_ID('dbo.RequestCustomDays'))
    DROP INDEX IX_RequestCustomDays_Requests ON dbo.RequestCustomDays;

-- Удаление таблиц (в обратном порядке зависимостей)
IF OBJECT_ID('dbo.NotificationRecipients', 'U') IS NOT NULL DROP TABLE dbo.NotificationRecipients;
IF OBJECT_ID('dbo.Notifications', 'U') IS NOT NULL DROP TABLE dbo.Notifications;
IF OBJECT_ID('dbo.MessageRecipients', 'U') IS NOT NULL DROP TABLE dbo.MessageRecipients;
IF OBJECT_ID('dbo.MessageTemplates', 'U') IS NOT NULL DROP TABLE dbo.MessageTemplates;
IF OBJECT_ID('dbo.RequestComments', 'U') IS NOT NULL DROP TABLE dbo.RequestComments;
IF OBJECT_ID('dbo.RequestPhotos', 'U') IS NOT NULL DROP TABLE dbo.RequestPhotos;
IF OBJECT_ID('dbo.RequestCustomDays', 'U') IS NOT NULL DROP TABLE dbo.RequestCustomDays;
IF OBJECT_ID('dbo.Requests', 'U') IS NOT NULL DROP TABLE dbo.Requests;
IF OBJECT_ID('dbo.UrgencyCategories', 'U') IS NOT NULL DROP TABLE dbo.UrgencyCategories;
IF OBJECT_ID('dbo.WorkCategories', 'U') IS NOT NULL DROP TABLE dbo.WorkCategories;
IF OBJECT_ID('dbo.ShopContractorChats', 'U') IS NOT NULL DROP TABLE dbo.ShopContractorChats;
IF OBJECT_ID('dbo.Shops', 'U') IS NOT NULL DROP TABLE dbo.Shops;
IF OBJECT_ID('dbo.RefreshTokens', 'U') IS NOT NULL DROP TABLE dbo.RefreshTokens;
IF OBJECT_ID('dbo.Users', 'U') IS NOT NULL DROP TABLE dbo.Users;
IF OBJECT_ID('dbo.Roles', 'U') IS NOT NULL DROP TABLE dbo.Roles;
IF OBJECT_ID('dbo.AuditLog', 'U') IS NOT NULL DROP TABLE dbo.AuditLog;

-- Создание таблиц

CREATE TABLE dbo.Roles (
    RoleID INT IDENTITY(1,1) NOT NULL,
    RoleName NVARCHAR(100) NOT NULL,
    CONSTRAINT PK_Roles PRIMARY KEY (RoleID),
    CONSTRAINT UQ_Roles_RoleName UNIQUE (RoleName)
);

CREATE TABLE dbo.Users (
    UserID INT IDENTITY(1,1) NOT NULL,
    Login NVARCHAR(100) NOT NULL,
    Password NVARCHAR(255) NOT NULL,
    FullName NVARCHAR(200) NULL,
    RoleID INT NOT NULL,
    ContactInfo NVARCHAR(400) NULL,
    TelegramID BIGINT NULL,
    CONSTRAINT PK_Users PRIMARY KEY (UserID),
    CONSTRAINT UQ_Users_Login UNIQUE (Login),
    CONSTRAINT FK_Users_Roles FOREIGN KEY (RoleID) REFERENCES dbo.Roles(RoleID)
);

CREATE TABLE dbo.Shops (
    ShopID INT IDENTITY(1,1) NOT NULL,
    ShopName NVARCHAR(150) NOT NULL,
    Address NVARCHAR(300) NULL,
    Email NVARCHAR(150) NULL,
    UserID INT NULL,
    CONSTRAINT PK_Shops PRIMARY KEY (ShopID),
    CONSTRAINT FK_Shops_Users FOREIGN KEY (UserID) REFERENCES dbo.Users(UserID),
    CONSTRAINT UQ_Shops_ShopName UNIQUE (ShopName)
);

CREATE TABLE dbo.ShopContractorChats(
    ShopContractorChatID INT IDENTITY(1,1) NOT NULL,
    ShopID INT NOT NULL,
    ContractorID INT NULL, -- Разрешаем NULL для общего чата магазина
    TelegramID BIGINT NOT NULL,
    CONSTRAINT PK_ShopContractorChats PRIMARY KEY (ShopContractorChatID),
    CONSTRAINT FK_ShopContractorChats_Shops FOREIGN KEY (ShopID) REFERENCES dbo.Shops(ShopID) ON DELETE CASCADE,
    CONSTRAINT FK_ShopContractorChats_Users FOREIGN KEY (ContractorID) REFERENCES dbo.Users(UserID) ON DELETE NO ACTION, -- Избегаем циклов при каскадном удалении
    CONSTRAINT UQ_ShopContractorChats_TelegramID UNIQUE (TelegramID),
    CONSTRAINT UQ_ShopContractorChats_Shop_User UNIQUE (ShopID, ContractorID)
);

CREATE TABLE dbo.WorkCategories (
    WorkCategoryID INT IDENTITY(1,1) NOT NULL,
    WorkCategoryName NVARCHAR(150) NOT NULL,
    CONSTRAINT PK_WorkCategories PRIMARY KEY (WorkCategoryID)
);

CREATE TABLE dbo.UrgencyCategories (
    UrgencyID INT IDENTITY(1,1) NOT NULL,
    UrgencyName NVARCHAR(100) NOT NULL,
    DefaultDays INT NULL,
    CONSTRAINT PK_UrgencyCategories PRIMARY KEY (UrgencyID)
);

CREATE TABLE dbo.Requests (
    RequestID INT IDENTITY(1,1) NOT NULL,
    Description NVARCHAR(MAX) NULL,
    ShopID INT NOT NULL,
    WorkCategoryID INT NOT NULL,
    UrgencyID INT NOT NULL,
    CreatedByUserID INT NOT NULL,
    AssignedContractorID INT NOT NULL,
    Status NVARCHAR(50) NOT NULL,
    CreatedAt DATETIME2 NOT NULL DEFAULT GETDATE(),
    ClosedAt DATETIME2 NULL,
    IsOverdue BIT NOT NULL DEFAULT 0,
    CONSTRAINT PK_Requests PRIMARY KEY (RequestID),
    CONSTRAINT FK_Requests_Shops FOREIGN KEY (ShopID) REFERENCES dbo.Shops(ShopID),
    CONSTRAINT FK_Requests_WorkCategories FOREIGN KEY (WorkCategoryID) REFERENCES dbo.WorkCategories(WorkCategoryID),
    CONSTRAINT FK_Requests_UrgencyCategories FOREIGN KEY (UrgencyID) REFERENCES dbo.UrgencyCategories(UrgencyID),
    CONSTRAINT FK_Requests_CreatedByUser FOREIGN KEY (CreatedByUserID) REFERENCES dbo.Users(UserID),
    CONSTRAINT FK_Requests_AssignedContractor FOREIGN KEY (AssignedContractorID) REFERENCES dbo.Users(UserID)
);

CREATE TABLE dbo.RequestCustomDays (
    RequestCustomDayID INT IDENTITY(1,1) NOT NULL,
    RequestID INT NOT NULL,
    Days INT NOT NULL,
    CONSTRAINT PK_RequestCustomDays PRIMARY KEY (RequestCustomDayID),
    CONSTRAINT FK_RequestCustomDays_Requests FOREIGN KEY (RequestID) REFERENCES dbo.Requests(RequestID) ON DELETE CASCADE
);

CREATE TABLE dbo.RequestPhotos (
    RequestPhotoID INT IDENTITY(1,1) NOT NULL,
    RequestID INT NOT NULL,
    ImageData VARBINARY(MAX),
    CONSTRAINT PK_RequestPhotos PRIMARY KEY (RequestPhotoID),
    CONSTRAINT FK_RequestPhotos_Requests FOREIGN KEY (RequestID) REFERENCES dbo.Requests(RequestID) ON DELETE CASCADE
);

CREATE TABLE dbo.RequestComments (
    CommentID INT IDENTITY(1,1) NOT NULL,
    RequestID INT NOT NULL,
    UserID INT NOT NULL,
    CommentText NVARCHAR(1000) NOT NULL,
    CreatedAt DATETIME2 NOT NULL DEFAULT GETDATE(),
    CONSTRAINT PK_RequestComments PRIMARY KEY (CommentID),
    CONSTRAINT FK_RequestComments_Requests FOREIGN KEY (RequestID) REFERENCES dbo.Requests(RequestID) ON DELETE CASCADE,
    CONSTRAINT FK_RequestComments_Users FOREIGN KEY (UserID) REFERENCES dbo.Users(UserID) ON DELETE NO ACTION -- Избегаем циклов
);

CREATE TABLE dbo.MessageTemplates (
    MessageID INT IDENTITY(1,1) NOT NULL,
    Title NVARCHAR(200) NOT NULL,
    Message NVARCHAR(MAX) NULL,
    CreatedAt DATETIME2 NOT NULL DEFAULT GETDATE(),
    ImageData VARBINARY(MAX) NULL,
    CONSTRAINT PK_MessageTemplates PRIMARY KEY (MessageID),
    CONSTRAINT UQ_MessageTemplates_Title UNIQUE (Title)
);

CREATE TABLE dbo.MessageRecipients (
    MessageRecipientsID INT IDENTITY(1,1) NOT NULL,
    MessageID INT NOT NULL,
    ShopContractorChatID INT NOT NULL,
    CONSTRAINT PK_MessageRecipients PRIMARY KEY (MessageRecipientsID),
    CONSTRAINT FK_MessageRecipients_MessageTemplates FOREIGN KEY (MessageID) REFERENCES dbo.MessageTemplates(MessageID) ON DELETE CASCADE,
    CONSTRAINT FK_MessageRecipients_ShopContractorChats FOREIGN KEY (ShopContractorChatID) REFERENCES dbo.ShopContractorChats(ShopContractorChatID) ON DELETE CASCADE
);

CREATE TABLE dbo.Notifications (
    NotificationID INT IDENTITY(1,1) NOT NULL,
    Title NVARCHAR(200) NOT NULL,
    Message NVARCHAR(MAX) NULL,
    ImageData VARBINARY(MAX) NULL,
    CronExpression NVARCHAR(100) NOT NULL,
    IsActive BIT NOT NULL DEFAULT 1,
    CONSTRAINT PK_Notifications PRIMARY KEY (NotificationID),
    CONSTRAINT UQ_Notifications_Title UNIQUE (Title)
);

CREATE TABLE dbo.NotificationRecipients (
    NotificationRecipientID INT IDENTITY(1,1) NOT NULL,
    NotificationID INT NOT NULL,
    ShopContractorChatID INT NOT NULL,
    CONSTRAINT PK_NotificationRecipients PRIMARY KEY (NotificationRecipientID),
    CONSTRAINT FK_NotificationRecipients_Notifications FOREIGN KEY (NotificationID) REFERENCES dbo.Notifications(NotificationID) ON DELETE CASCADE,
    CONSTRAINT FK_NotificationRecipients_ShopContractorChats FOREIGN KEY (ShopContractorChatID) REFERENCES dbo.ShopContractorChats(ShopContractorChatID) ON DELETE CASCADE
);

CREATE TABLE dbo.AuditLog (
    LogID INT IDENTITY(1,1),
    TableName NVARCHAR(100),
    Action NVARCHAR(10),
    RecordID INT,
    UserID INT NULL,
    LogDate DATETIME2 DEFAULT GETDATE(),
    Changes NVARCHAR(MAX) NULL,
    CONSTRAINT PK_AuditLog PRIMARY KEY (LogID)
);

CREATE TABLE dbo.RefreshTokens (
    TokenID INT IDENTITY(1,1),
    UserID INT NOT NULL,
    TokenHash VARCHAR(512) NOT NULL,
    IssuedAt DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    ExpiresAt DATETIME2 NOT NULL,
    CONSTRAINT PK_RefreshTokens PRIMARY KEY (TokenID),
    CONSTRAINT FK_RefreshTokens_Users_ FOREIGN KEY (UserID) REFERENCES dbo.Users(UserID) ON DELETE CASCADE
);

CREATE INDEX IX_Requests_ShopID ON dbo.Requests(ShopID);
CREATE INDEX IX_Requests_Status ON dbo.Requests(Status);
CREATE INDEX IX_Requests_WorkCategoryID ON dbo.Requests(WorkCategoryID);
CREATE INDEX IX_Requests_UrgencyID ON dbo.Requests(UrgencyID);
CREATE INDEX IX_RequestComments_RequestID ON dbo.RequestComments(RequestID);
CREATE INDEX IX_RequestCustomDays_Requests ON dbo.RequestCustomDays(RequestID);
CREATE UNIQUE INDEX UQ_Users_TelegramID_Filtered ON dbo.Users(TelegramID) WHERE TelegramID IS NOT NULL;