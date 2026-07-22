import { Pipe, PipeTransform } from '@angular/core';
import { InsightProposalSummary, ProposalStatus } from './insight.models';
@Pipe({ name: 'proposalCount', standalone: true })
export class ProposalCountPipe implements PipeTransform {
  transform(items: readonly InsightProposalSummary[], status: ProposalStatus): number {
    return items.filter((item) => item.status === status).length;
  }
}
