/* @flow */

import React from "react";

import AuditContent from "../components/AuditContent";
import AuditDashboard from "../containers/AuditDashboard";
import AuditTable from "../containers/AuditTable";

import OpenInMetabase from "../components/OpenInMetabase";

import EntityName from "metabase/entities/containers/EntityName";

import * as Urls from "metabase/lib/urls";

import * as QuestionCards from "../lib/cards/question_detail";

type Props = {
  params: { [key: string]: string },
};

const AuditQuestionDetail = ({ params, ...props }: Props) => {
  const questionId = parseInt(params.questionId);
  return (
    <AuditContent
      {...props}
      title={<EntityName entityType="questions" entityId={questionId} />}
      subtitle={<OpenInMetabase to={Urls.question(questionId)} />}
      tabs={AuditQuestionDetail.tabs}
      questionId={questionId}
    />
  );
};

const AuditQuestionActivityTab = ({ questionId }) => (
  <AuditDashboard
    cards={[
      [{ x: 0, y: 4, w: 18, h: 4 }, QuestionCards.viewsByTime(questionId)],
    ]}
  />
);

const AuditQuestionRevisionsTab = ({ questionId }) => (
  <AuditTable table={QuestionCards.revisionHistory(questionId)} />
);

const AuditQuestionAuditLogTab = ({ questionId }) => (
  <AuditTable table={QuestionCards.auditLog(questionId)} />
);

AuditQuestionDetail.tabs = [
  { path: "activity", title: "Activity", component: AuditQuestionActivityTab },
  { path: "details", title: "Details" },
  {
    path: "revisions",
    title: "Revision history",
    component: AuditQuestionRevisionsTab,
  },
  { path: "log", title: "Audit log", component: AuditQuestionAuditLogTab },
];

export default AuditQuestionDetail;
