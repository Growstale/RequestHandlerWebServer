-- =============================================
-- Создание таблиц (Idempotent script)
-- =============================================

-- 1. Roles
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Roles' AND xtype='U')
BEGIN
CREATE TABLE dbo.Roles (
                           RoleID INT IDENTITY(1,1) NOT NULL,
                           RoleName NVARCHAR(100) NOT NULL,
                           CONSTRAINT PK_Roles PRIMARY KEY (RoleID),
                           CONSTRAINT UQ_Roles_RoleName UNIQUE (RoleName)
)
END;

-- 2. Users
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Users' AND xtype='U')
BEGIN
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
)
END;

-- 3. Shops
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Shops' AND xtype='U')
BEGIN
CREATE TABLE dbo.Shops (
                           ShopID INT IDENTITY(1,1) NOT NULL,
                           ShopName NVARCHAR(150) NOT NULL,
                           Address NVARCHAR(300) NULL,
                           Email NVARCHAR(150) NULL,
                           UserID INT NULL,
                           CONSTRAINT PK_Shops PRIMARY KEY (ShopID),
                           CONSTRAINT FK_Shops_Users FOREIGN KEY (UserID) REFERENCES dbo.Users(UserID),
                           CONSTRAINT UQ_Shops_ShopName UNIQUE (ShopName)
)
END;

-- 4. ShopContractorChats
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='ShopContractorChats' AND xtype='U')
BEGIN
CREATE TABLE dbo.ShopContractorChats(
                                        ShopContractorChatID INT IDENTITY(1,1) NOT NULL,
                                        ShopID INT NOT NULL,
                                        ContractorID INT NULL,
                                        TelegramID BIGINT NOT NULL,
                                        CONSTRAINT PK_ShopContractorChats PRIMARY KEY (ShopContractorChatID),
                                        CONSTRAINT FK_ShopContractorChats_Shops FOREIGN KEY (ShopID) REFERENCES dbo.Shops(ShopID) ON DELETE CASCADE,
                                        CONSTRAINT FK_ShopContractorChats_Users FOREIGN KEY (ContractorID) REFERENCES dbo.Users(UserID) ON DELETE NO ACTION,
                                        CONSTRAINT UQ_ShopContractorChats_TelegramID UNIQUE (TelegramID),
                                        CONSTRAINT UQ_ShopContractorChats_Shop_User UNIQUE (ShopID, ContractorID)
)
END;

-- 5. WorkCategories
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='WorkCategories' AND xtype='U')
BEGIN
CREATE TABLE dbo.WorkCategories (
                                    WorkCategoryID INT IDENTITY(1,1) NOT NULL,
                                    WorkCategoryName NVARCHAR(150) NOT NULL,
                                    CONSTRAINT PK_WorkCategories PRIMARY KEY (WorkCategoryID)
)
END;

-- 6. UrgencyCategories
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='UrgencyCategories' AND xtype='U')
BEGIN
CREATE TABLE dbo.UrgencyCategories (
                                       UrgencyID INT IDENTITY(1,1) NOT NULL,
                                       UrgencyName NVARCHAR(100) NOT NULL,
                                       DefaultDays INT NULL,
                                       CONSTRAINT PK_UrgencyCategories PRIMARY KEY (UrgencyID)
)
END;

-- 7. Requests
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Requests' AND xtype='U')
BEGIN
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
)
END;

-- 8. RequestCustomDays
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RequestCustomDays' AND xtype='U')
BEGIN
CREATE TABLE dbo.RequestCustomDays (
                                       RequestCustomDayID INT IDENTITY(1,1) NOT NULL,
                                       RequestID INT NOT NULL,
                                       Days INT NOT NULL,
                                       CONSTRAINT PK_RequestCustomDays PRIMARY KEY (RequestCustomDayID),
                                       CONSTRAINT FK_RequestCustomDays_Requests FOREIGN KEY (RequestID) REFERENCES dbo.Requests(RequestID) ON DELETE CASCADE
)
END;

-- 9. RequestPhotos
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RequestPhotos' AND xtype='U')
BEGIN
CREATE TABLE dbo.RequestPhotos (
                                   RequestPhotoID INT IDENTITY(1,1) NOT NULL,
                                   RequestID INT NOT NULL,
                                   ImageData VARBINARY(MAX),
                                   CONSTRAINT PK_RequestPhotos PRIMARY KEY (RequestPhotoID),
                                   CONSTRAINT FK_RequestPhotos_Requests FOREIGN KEY (RequestID) REFERENCES dbo.Requests(RequestID) ON DELETE CASCADE
)
END;

-- 10. RequestComments
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RequestComments' AND xtype='U')
BEGIN
CREATE TABLE dbo.RequestComments (
                                     CommentID INT IDENTITY(1,1) NOT NULL,
                                     RequestID INT NOT NULL,
                                     UserID INT NOT NULL,
                                     CommentText NVARCHAR(1000) NOT NULL,
                                     CreatedAt DATETIME2 NOT NULL DEFAULT GETDATE(),
                                     CONSTRAINT PK_RequestComments PRIMARY KEY (CommentID),
                                     CONSTRAINT FK_RequestComments_Requests FOREIGN KEY (RequestID) REFERENCES dbo.Requests(RequestID) ON DELETE CASCADE,
                                     CONSTRAINT FK_RequestComments_Users FOREIGN KEY (UserID) REFERENCES dbo.Users(UserID) ON DELETE NO ACTION
)
END;

-- 11. MessageTemplates
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='MessageTemplates' AND xtype='U')
BEGIN
CREATE TABLE dbo.MessageTemplates (
                                      MessageID INT IDENTITY(1,1) NOT NULL,
                                      Title NVARCHAR(200) NOT NULL,
                                      Message NVARCHAR(MAX) NULL,
                                      CreatedAt DATETIME2 NOT NULL DEFAULT GETDATE(),
                                      ImageData VARBINARY(MAX) NULL,
                                      CONSTRAINT PK_MessageTemplates PRIMARY KEY (MessageID),
                                      CONSTRAINT UQ_MessageTemplates_Title UNIQUE (Title)
)
END;

-- 12. MessageRecipients
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='MessageRecipients' AND xtype='U')
BEGIN
CREATE TABLE dbo.MessageRecipients (
                                       MessageRecipientsID INT IDENTITY(1,1) NOT NULL,
                                       MessageID INT NOT NULL,
                                       ShopContractorChatID INT NOT NULL,
                                       CONSTRAINT PK_MessageRecipients PRIMARY KEY (MessageRecipientsID),
                                       CONSTRAINT FK_MessageRecipients_MessageTemplates FOREIGN KEY (MessageID) REFERENCES dbo.MessageTemplates(MessageID) ON DELETE CASCADE,
                                       CONSTRAINT FK_MessageRecipients_ShopContractorChats FOREIGN KEY (ShopContractorChatID) REFERENCES dbo.ShopContractorChats(ShopContractorChatID) ON DELETE CASCADE
)
END;

-- 13. Notifications
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
                                   CONSTRAINT UQ_Notifications_Title UNIQUE (Title)
)
END;

-- 14. NotificationRecipients
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='NotificationRecipients' AND xtype='U')
BEGIN
CREATE TABLE dbo.NotificationRecipients (
                                            NotificationRecipientID INT IDENTITY(1,1) NOT NULL,
                                            NotificationID INT NOT NULL,
                                            ShopContractorChatID INT NOT NULL,
                                            CONSTRAINT PK_NotificationRecipients PRIMARY KEY (NotificationRecipientID),
                                            CONSTRAINT FK_NotificationRecipients_Notifications FOREIGN KEY (NotificationID) REFERENCES dbo.Notifications(NotificationID) ON DELETE CASCADE,
                                            CONSTRAINT FK_NotificationRecipients_ShopContractorChats FOREIGN KEY (ShopContractorChatID) REFERENCES dbo.ShopContractorChats(ShopContractorChatID) ON DELETE CASCADE
)
END;

-- 15. AuditLog
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='AuditLog' AND xtype='U')
BEGIN
CREATE TABLE dbo.AuditLog (
                              LogID INT IDENTITY(1,1),
                              TableName NVARCHAR(100),
                              Action NVARCHAR(10),
                              RecordID INT,
                              UserID INT NULL,
                              LogDate DATETIME2 DEFAULT GETDATE(),
                              Changes NVARCHAR(MAX) NULL,
                              CONSTRAINT PK_AuditLog PRIMARY KEY (LogID)
)
END;

-- 16. RefreshTokens
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='RefreshTokens' AND xtype='U')
BEGIN
CREATE TABLE dbo.RefreshTokens (
                                   TokenID INT IDENTITY(1,1),
                                   UserID INT NOT NULL,
                                   TokenHash VARCHAR(512) NOT NULL,
                                   IssuedAt DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
                                   ExpiresAt DATETIME2 NOT NULL,
                                   CONSTRAINT PK_RefreshTokens PRIMARY KEY (TokenID),
                                   CONSTRAINT FK_RefreshTokens_Users_ FOREIGN KEY (UserID) REFERENCES dbo.Users(UserID) ON DELETE CASCADE
)
END;

-- =============================================
-- Создание индексов
-- =============================================

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='IX_Requests_ShopID' AND object_id = OBJECT_ID('dbo.Requests'))
BEGIN
CREATE INDEX IX_Requests_ShopID ON dbo.Requests(ShopID)
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='IX_Requests_Status' AND object_id = OBJECT_ID('dbo.Requests'))
BEGIN
CREATE INDEX IX_Requests_Status ON dbo.Requests(Status)
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='IX_Requests_WorkCategoryID' AND object_id = OBJECT_ID('dbo.Requests'))
BEGIN
CREATE INDEX IX_Requests_WorkCategoryID ON dbo.Requests(WorkCategoryID)
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='IX_Requests_UrgencyID' AND object_id = OBJECT_ID('dbo.Requests'))
BEGIN
CREATE INDEX IX_Requests_UrgencyID ON dbo.Requests(UrgencyID)
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='IX_RequestComments_RequestID' AND object_id = OBJECT_ID('dbo.RequestComments'))
BEGIN
CREATE INDEX IX_RequestComments_RequestID ON dbo.RequestComments(RequestID)
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='IX_RequestCustomDays_Requests' AND object_id = OBJECT_ID('dbo.RequestCustomDays'))
BEGIN
CREATE INDEX IX_RequestCustomDays_Requests ON dbo.RequestCustomDays(RequestID)
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name='UQ_Users_TelegramID_Filtered' AND object_id = OBJECT_ID('dbo.Users'))
BEGIN
CREATE UNIQUE INDEX UQ_Users_TelegramID_Filtered ON dbo.Users(TelegramID) WHERE TelegramID IS NOT NULL
END;