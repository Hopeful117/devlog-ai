export interface DecisionDetail {
  readonly id: string;
  readonly projectId: string;
  readonly proposalId: string | null;
  readonly title: string;
  readonly context: string;
  readonly choice: string;
  readonly rationale: string;
  readonly consequences: string | null;
  readonly createdAt: string | null;
  readonly updatedAt: string | null;
}
